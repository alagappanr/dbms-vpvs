package edu.buffalo.cse562.logicalPlan;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import jdbm.RecordManager;
import jdbm.RecordManagerFactory;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectBody;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import edu.buffalo.cse562.logger.logManager;
import edu.buffalo.cse562.physicalPlan.AggregateOperator;
import edu.buffalo.cse562.physicalPlan.BNLJoinOperator;
import edu.buffalo.cse562.physicalPlan.ExternalSort;
import edu.buffalo.cse562.physicalPlan.FromItemParser;
import edu.buffalo.cse562.physicalPlan.GroupbyOperator;
import edu.buffalo.cse562.physicalPlan.HHJoinOperator;
import edu.buffalo.cse562.physicalPlan.IndexAggregateOperator;
import edu.buffalo.cse562.physicalPlan.IndexNLJoinOperator;
import edu.buffalo.cse562.physicalPlan.LimitOperator;
import edu.buffalo.cse562.physicalPlan.Operator;
import edu.buffalo.cse562.physicalPlan.OrderByOperator;
import edu.buffalo.cse562.physicalPlan.ProjectionOperator;
import edu.buffalo.cse562.physicalPlan.SelectionOperator;
import edu.buffalo.cse562.physicalPlan.SortMergeJoinOperator;
import edu.buffalo.cse562.physicalPlan.TupleStruct;
import edu.buffalo.cse562.sql.expression.evaluator.AndVisitor;
import edu.buffalo.cse562.sql.expression.evaluator.CalcTools;
import edu.buffalo.cse562.sql.expression.evaluator.ColumnFetcher;
import edu.buffalo.cse562.sql.expression.evaluator.EqualityCheck;
import edu.buffalo.cse562.sql.expression.evaluator.ExpressionSplitter;
import edu.buffalo.cse562.sql.expression.evaluator.TableNameFetcher;
import edu.buffalo.cse562.structure.Datum;

public class components {

	Map<String, ArrayList<Column>> masterTableMap;
	Map<String, ArrayList<String>> masterTableColTypeMap;
//	Map<String, ArrayList<Column>> tableMap;
//	Map<String, ArrayList<String>> tableColTypeMap;
	Map<String, ArrayList<Integer>> tableRemoveCols;
	ArrayList<SelectExpressionItem> projectStmt;
	Map<String, List<String>> indexNameListMap = new HashMap<String, List<String>>();
	ArrayList tableJoins;
	ArrayList<String> tblJoinStr;
	Expression whereClause;
	String tableDir;
	String swapDir;
	String indexDir;
	String treeMapName;
	FromItem fromItem;
	SelectBody selectBody;
	private List orderbyElements;
	private Limit limit;
	StringBuffer planPrint;
	HashMap<String, ArrayList<Expression>> singleTableMap = new HashMap<String, ArrayList<Expression>>();
	ArrayList<Expression> eList = new ArrayList<Expression>();
	List<Integer> intList = new ArrayList<Integer>();
	ArrayList<Expression> onExpressionList = new ArrayList<Expression>();
	ArrayList<List<String>> onTableLists = new ArrayList<List<String>>();
	ArrayList<Expression> otherList = new ArrayList<Expression>();
	ArrayList<String> joinedTables = new ArrayList<String>();
	Long minFileSize;
	Long fileThreshold;
	Boolean firstTime;
	//	Map<String, String> joinCol;
	String sqlQuery;
	Map<String, List<String>> metaInfo;

	public components() {
		masterTableMap = new HashMap<String, ArrayList<Column>>();
		masterTableColTypeMap = new HashMap<String, ArrayList<String>>();
		planPrint = new StringBuffer();
		this.fileThreshold = Long.valueOf(50000000);
	}

	public void initializeParam() {
		projectStmt = new ArrayList<SelectExpressionItem>();
//		tableMap = new HashMap<String, ArrayList<Column>>();
//		tableColTypeMap = new HashMap<String, ArrayList<String>>();
		tableRemoveCols = new HashMap<String, ArrayList<Integer>>();
		tblJoinStr = new ArrayList<String>();
		this.firstTime = true;
	}

	public void addProjectStmts(List<SelectExpressionItem> list) {
		projectStmt.addAll(list);
	}

	public void addWhereConditions(Expression where) {
		whereClause = where;
	}

	public void setSelectBody(SelectBody selectBody) {
		this.selectBody = selectBody;
	}

	public void addToPlan(String s) {
		s += "\n" + planPrint.toString();
		planPrint = new StringBuffer(s);
	}

	public void printPlan() {
		System.out.println();
		System.out.println("-------------------------------------------------");
		System.out.println(planPrint.toString());
		System.out.println("-------------------------------------------------");
		System.out.println();
	}

	public Operator executePhysicalPlan() {
		Operator oper = null;
		Boolean singleTableFlag = false;
		Map<String, List<Integer>> tableExpressionCounter = null;
		Map<String, ArrayList<Expression>> indexMap = new HashMap<String, ArrayList<Expression>>();
		Map<String, String> treeMap = new HashMap<String, String>();
		Map<String, ArrayList<Expression>> selectionMap = new HashMap<String, ArrayList<Expression>>();
		List<Expression> expList = new ArrayList<Expression>();

		if(!(tableRemoveCols!=null && tableRemoveCols.size() > 0))
			columnReducer();

		
		boolean isFunction = false;
		for (SelectExpressionItem sei : projectStmt) {
			Expression e = sei.getExpression();
			if (e instanceof Function)
				isFunction = true;
		}
		
		if(whereClause == null && isFunction) {
			oper = new IndexAggregateOperator(projectStmt,indexDir,(Table)fromItem, masterTableMap, masterTableColTypeMap, metaInfo);
			return oper;
		}
		
		if (whereClause != null) {

			try {
				AndVisitor calc = new AndVisitor();
				whereClause.accept(calc);
				//				System.out.println(calc.getList());
				expList = calc.getList();
			} catch (UnsupportedOperationException e) {
				expList.add(whereClause);
			}
			for(int i = 0; i < expList.size(); i++) {
				Expression e = expList.get(i);
				ExpressionSplitter split = new ExpressionSplitter();
				e.accept(split);
				if(split.getTableList()!=null&&split.getTableList().get(0)!=null){
					if (split.getColumnCounter() == 1) {

						eList = singleTableMap.get(split.getTableList().get(0));
						if (eList == null) {
							eList = new ArrayList<Expression>();
						}
						eList.add(e);
						singleTableMap.put(split.getTableList().get(0), eList);
					} else if (split.getColumnCounter() == 2) {
						onExpressionList.add(e);
						onTableLists.add(split.getTableList());
					} else {
						otherList.add(e);
					}
				} else {
					singleTableFlag = true;

					eList = singleTableMap.get("__NONE__");
					if (eList == null) {
						// System.out.println();
						eList = new ArrayList<Expression>();
					}
					eList.add(e);
					//intList.add(eList.size()-1);
					//tableExpressionCounter.put("__NONE__", intList);
					singleTableMap.put("__NONE__", eList);					
				}
			}
		}
		String finalIndex = null;
		String treeMapNameFinal = null;
		String tblName = "";
		for(String s: singleTableMap.keySet()){
			if(s=="__NONE__"){
				TableNameFetcher tnf = new TableNameFetcher();
				fromItem.accept(tnf);
				tblName = tnf.getTableName();
				singleTableMap.put(tblName, singleTableMap.get("__NONE__"));
			} else {
				tblName = s;
			}
			File f = new File(indexDir + File.separator + tblName +".metadata");
			if (f.exists()){
				BufferedReader br = null;
				try {
					br = new BufferedReader(new FileReader(f));
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				}

				String indexLine = null, indexFullLine=null;
				Boolean isindex = null;
				String fullExpString = "";
				for(int i = 0; i < singleTableMap.get(tblName).size(); i++){
					String expString = singleTableMap.get(tblName).get(i).toString();
					int lastIndex = 0;
					int count = 0;

					while(lastIndex != -1){

						lastIndex = expString.indexOf(tblName,lastIndex);
						if( lastIndex != -1){
							count ++;
							lastIndex+=tblName.length();
						}
					}
					if(count<2){
						fullExpString = fullExpString.concat(singleTableMap.get(tblName).get(i).toString());
					}
				}

				try {
					while ((indexFullLine = br.readLine()) != null) {
//						System.out.println(indexFullLine);
						String[] parts = indexFullLine.split("::",2);
						indexLine = parts[0];
						treeMapName = parts[1];
						isindex = true;
						if(indexLine.contains(",")){
							List<String> indexList = Arrays.asList(indexLine.split(","));
							for (String index: indexList) {
//								System.out.println("indexList a :: "+indexList);
//								System.out.println("fullExpString a :: "+ fullExpString);
								if(!fullExpString.toLowerCase().contains(index.trim())){
									isindex = false;
								}
//								System.out.println("isindex a :: "+isindex);
							}
						} else {
//							System.out.println("indexLine b :: "+indexLine);
//							System.out.println("fullExpString b :: "+ fullExpString);
							if(!fullExpString.toLowerCase().contains(indexLine)){
								isindex = false;
							}
//							System.out.println("isindex b :: "+isindex);
						}
						if(isindex==true){
							finalIndex = indexLine;
							treeMapNameFinal = treeMapName;
							break;
						}
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
//				System.out.println("Final Index is "+ finalIndex);
				for(int i = 0; i < singleTableMap.get(tblName).size(); i++){
					String expString = singleTableMap.get(tblName).get(i).toString();
					int lastIndex = 0;
					int count = 0;

					while(lastIndex != -1){

						lastIndex = expString.indexOf(tblName,lastIndex);
						if( lastIndex != -1){
							count ++;
							lastIndex+=tblName.length();
						}
					}

					if(count < 2 && finalIndex!=null && finalIndex.contains(",")){
						treeMap.put(tblName, treeMapNameFinal);
						List<String> indexList = Arrays.asList(finalIndex.split(","));
						for (String index: indexList) {
							System.out.println(singleTableMap.get(tblName).get(i).toString());
							System.out.println(index);
							if(singleTableMap.get(tblName).get(i).toString().contains(index)){
								eList = indexMap.get(tblName);
								if (eList == null) {
									// System.out.println();
									eList = new ArrayList<Expression>();
								}
								eList.add(singleTableMap.get(tblName).get(i));
								indexNameListMap.put(tblName, indexList);
								indexMap.put(tblName, eList);
							} else {
								eList = selectionMap.get(tblName);
								if (eList == null) {
									// System.out.println();
									eList = new ArrayList<Expression>();
								}
								eList.add(singleTableMap.get(tblName).get(i));
								selectionMap.put(tblName, eList);
							}
						}
					} else {
//						System.out.println("Else part");
//						System.out.println("Count :: " + count);
//						System.out.println("finalIndex :: " + finalIndex);
//						System.out.println("singleTableMap.get(tblName).get(i).toString() :: " + singleTableMap.get(tblName).get(i).toString());
//						System.out.println("singleTableMap :: " + singleTableMap);
						if(count < 2 && finalIndex!=null && singleTableMap.get(tblName).get(i).toString().toLowerCase().contains(finalIndex)){
							//							System.out.println("Contains");
							treeMap.put(tblName, treeMapNameFinal);
							eList = indexMap.get(tblName);
							if (eList == null) {
								// System.out.println();
								eList = new ArrayList<Expression>();
							}
							eList.add(singleTableMap.get(tblName).get(i));
							indexNameListMap.put(tblName, Arrays.asList(finalIndex));
							indexMap.put(tblName, eList);
						} else {
							//							System.out.println("Not contains");
							eList = selectionMap.get(tblName);
							if (eList == null) {
								// System.out.println();
								eList = new ArrayList<Expression>();
							}
							eList.add(singleTableMap.get(tblName).get(i));
							selectionMap.put(tblName, eList);
						}
					}
				}
//								System.out.println("indexMap :: " + indexMap);
//								System.out.println("selectionMap :: " + selectionMap);
//								System.out.println("treeMap :: " + treeMap);
				//				System.out.println(indexMap.get("part"));
				//				System.out.println(treeMap.get("part"));
				try {
					br.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

		}
		
//		System.out.println("onExpressionList :: "+ onExpressionList);
//		System.out.println("onTableLists :: " + onTableLists);
//		System.out.println("otherList :: " + otherList);
//		System.out.println("joinedTables :: " + joinedTables);
//		 System.out.println("singleTableMap :: "+ singleTableMap);
		//		System.out.println("TableDir----->"+tableDir);
		//		System.out.println("IndexDir----->"+indexDir);
		//		System.out.println("Keys in selection MAP:" + selectionMap.keySet());
		//		System.out.println("Keys in index MAP:" + indexMap.keySet());
		//		System.out.println("Keys in tree MAP:" + treeMap.keySet());
		//		System.out.println("Indexes in tree MAP:" + treeMap.get("part"));
		//		System.out.println("Expressions in index MAP:" + indexMap.get("part"));
		//		System.out.println("Expressions in selection MAP:" + selectionMap.get("part"));
		FromItemParser fip = new FromItemParser(tableDir, masterTableMap,
				masterTableColTypeMap, swapDir, tableRemoveCols, indexDir, treeMap, indexMap, indexNameListMap);
		fip.setExprList(onExpressionList);
		fip.setJoins(tblJoinStr);
		fromItem.accept(fip);
		oper = fip.getOperator();
		addToPlan(fip.getPlan().toString());
		// System.out.println("First table"+fip.getOperatorTableName());
		String operTable = fip.getOperatorTableName();
		if(singleTableFlag==true){
			eList = selectionMap.get("__NONE__");
		} else {
			eList = selectionMap.get(operTable);	
		}
		//		System.out.println(eList);
		Expression leftWhereClause = null;
		if (eList != null) {
			leftWhereClause = eList.get(0);
			for (int i = 1; i < eList.size(); i++) {
				leftWhereClause = new AndExpression(leftWhereClause,
						eList.get(i));
			}
			//			System.out.println(leftWhereClause);
			oper = new SelectionOperator(oper, leftWhereClause);
			addToPlan("[Selection on :: " + operTable + " Expr :: "
					+ leftWhereClause.toString() + "]");
		}
		joinedTables.add(operTable);
		//		printPlan();
		
		if (tableJoins != null) {
			Map<String, RecordManager> recManMap = new HashMap<String, RecordManager>();
			TupleStruct.setJoinCondition(true);
			Iterator joinIte = tableJoins.iterator();
			Map<String, String> joinCol = new HashMap<String, String>();
			Map<String, Integer> joinCount = new HashMap<String, Integer>();
			while (joinIte.hasNext()) {
				Join joinTable = (Join) joinIte.next();
				fip = new FromItemParser(tableDir, masterTableMap, masterTableColTypeMap, swapDir, tableRemoveCols, indexDir, treeMap, indexMap, indexNameListMap);
				fip.setExprList(onExpressionList);
				fip.setJoins(tblJoinStr);
				joinTable.getRightItem().accept(fip);
				Operator rightOper = fip.getOperator();
				addToPlan(fip.getPlan().toString());
				// System.out.println("NAME"+fip.getOperatorTableName());
				// System.out.println("Right table "+fip.getOperatorTableName());
				String rightTable = fip.getOperatorTableName();
				eList = selectionMap.get(rightTable);

				Expression rightWhereClause = null;
				if (eList != null) {
					rightWhereClause = eList.get(0);
					for (int i = 1; i < eList.size(); i++) {
						rightWhereClause = new AndExpression(rightWhereClause,
								eList.get(i));
					}
					// System.out.println(rightWhereClause);
					rightOper = new SelectionOperator(rightOper,
							rightWhereClause);
					addToPlan("[Selection on :: " + rightTable + " Expr :: "
							+ rightWhereClause.toString() + "]");
				}
				// System.out.println(onExpressionList);
				// System.out.println(onTableLists);
				Expression onExpression = null;
				if (joinTable.getOnExpression() == null) {
					joinedTables.add(rightTable);
					for (int i = 0; i < onTableLists.size(); i++) {
						Boolean onExpFlag = true;
						for (String tableName : onTableLists.get(i)) {
							if (!joinedTables.contains(tableName)) {
								onExpFlag = false;
							}
						}
						if (onExpFlag == true) {
							onExpression = onExpressionList.get(i);
							onExpressionList.remove(i);
							onTableLists.remove(i);
							break;
						}
					}
				} else {
					onExpression = joinTable.getOnExpression();
				}
				//				System.out.println("Joined tables---"+joinedTables);
				//				System.out.println("join on condition"+onExpression);
//				printPlan();
				Boolean equalityCheck;

				if (onExpression != null) {
					EqualityCheck ec = new EqualityCheck();
					try {
						onExpression.accept(ec);
						equalityCheck = true;
					} catch (Exception e) {
						equalityCheck = false;
					}
					if (!equalityCheck) {
						oper = new BNLJoinOperator(oper, rightOper,
								onExpression);
						addToPlan("[Block Nested Join on :: " + joinedTables
								+ " and " + rightTable + " Expr :: "
								+ onExpression.toString() + "]");
					} else {

						//						System.out.println(onExpression.toString());
						ColumnFetcher cfetch = new ColumnFetcher(rightTable);
						onExpression.accept(cfetch);
						Column lcol = cfetch.getLeftCol();
						Column rcol = cfetch.getRightCol();
						//						if(indexMap.containsKey(rightTable)) {
						finalIndex = "";
						treeMapNameFinal = "";
//						System.out.println("LCOL is " + lcol.getTable());
//						System.out.println("RCOL is " + rcol.getTable());
//						System.out.println("indexMap :: "+indexMap);
						List onFlyTables = TupleStruct.getInFlyTables();
//						System.out.println("TupleStruct.getInFlyTables() :: "+TupleStruct.getInFlyTables());
						if (!onFlyTables.contains(lcol.getTable().toString())||!onFlyTables.contains(rcol.getTable().toString())){
							//							System.out.println("Either of the index is missing");
							if(!onFlyTables.contains(lcol.getTable().toString())){
								//								System.out.println("Left index is missing");
//								System.out.println("Lcol Table :: " + lcol.getTable().toString());
								String tableName = lcol.getTable().toString().toLowerCase();
								if("n1".equals(tableName) || "n2".equals(tableName)) {
									tableName = "nation";
								}
								File f = new File(indexDir + File.separator + tableName +".metadata");
								if (f.exists()){
									BufferedReader br = null;
									try {
										br = new BufferedReader(new FileReader(f));
									} catch (FileNotFoundException e) {
										e.printStackTrace();
									}

									String indexLine = null, indexFullLine=null;
									Boolean isindex = null;
									try {
										while ((indexFullLine = br.readLine()) != null) {
											//											System.out.println("Each index is "+indexFullLine);
											String[] parts = indexFullLine.split("::",2);
											indexLine = parts[0];
											treeMapName = parts[1];
											//											System.out.println(indexLine);
											if(!indexLine.contains(",")){
												//												System.out
												//														.println("Does not contain comma");
												//												System.out
												//														.println(lcol.getColumnName());
												if(indexLine.equalsIgnoreCase(lcol.getColumnName())){
													isindex = true;
												}
											}
											if(isindex!=null && isindex==true){
												finalIndex = indexLine;
												treeMapNameFinal = treeMapName;
												break;
											}
										}
									} catch (IOException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}
								}

								//								System.out.println(finalIndex);
								//								System.out.println(treeMapNameFinal);
								RecordManager recMan = null;
								if(recManMap.containsKey(tableName)) {
									recMan = recManMap.get(tableName);
								} else {
									try {
										recMan = RecordManagerFactory.createRecordManager(indexDir
												+ File.separator + tableName);
										recManMap.put(tableName,recMan);
									} catch (IOException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}
								}
								oper = new IndexNLJoinOperator(rightOper, treeMapNameFinal, indexDir, 
										tableName, operTable, rcol,lcol,masterTableMap.get(tableName),
										masterTableColTypeMap.get(tableName), recMan);
								recMan = null;
								TupleStruct.addInFlyTables(operTable);
								addToPlan("[IndexNLJoinOperator 1 on :: left ::" + rcol + " right ::"
										+ operTable + " :: "+ treeMapNameFinal + "]");
							} else {
								//								System.out.println("Right index is missing");
//								System.out.println("Right Col Table :: " + rcol.getTable().toString());
								String tableName = rcol.getTable().toString().toLowerCase();
								if("n1".equals(tableName) || "n2".equals(tableName)) {
									tableName = "nation";
								}
								File f = new File(indexDir + File.separator + tableName +".metadata");
								if (f.exists()){
									BufferedReader br = null;
									try {
										br = new BufferedReader(new FileReader(f));
									} catch (FileNotFoundException e) {
										e.printStackTrace();
									}

									String indexLine = null, indexFullLine=null;
									Boolean isindex = null;
									try {
										while ((indexFullLine = br.readLine()) != null) {
											//											System.out.println("Each index is "+indexFullLine);
											String[] parts = indexFullLine.split("::",2);
											indexLine = parts[0];
											treeMapName = parts[1];
											//											System.out.println(indexLine);
											if(!indexLine.contains(",")){
												//												System.out
												//														.println("Does not contain comma");
												//												System.out
												//														.println(rcol.getColumnName());
												if(indexLine.equalsIgnoreCase(rcol.getColumnName())){
													isindex = true;
												}
											}
											if(isindex!=null && isindex==true){
												finalIndex = indexLine;
												treeMapNameFinal = treeMapName;
												break;
											}
										}
									} catch (IOException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}
								}

								//								System.out.println(finalIndex);
								//								System.out.println(treeMapNameFinal);
								//								System.out.println("Right index is missing");
								RecordManager recMan = null;
								if(recManMap.containsKey(tableName)) {
									recMan = recManMap.get(tableName);
								} else {
									try {
										recMan = RecordManagerFactory.createRecordManager(indexDir
												+ File.separator + tableName);
										recManMap.put(tableName,recMan);
									} catch (IOException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}
								}
								oper = new IndexNLJoinOperator(oper, treeMapNameFinal, indexDir, tableName,rightTable,lcol,rcol,
										masterTableMap.get(tableName),masterTableColTypeMap.get(tableName), 
										recMan);
								recMan = null;
								TupleStruct.addInFlyTables(rightTable);
								addToPlan("[IndexNLJoinOperator 2 on left :: " + lcol + " :: right ::"
										+ rightTable + " :: "+ treeMapNameFinal + "]");
							}
							
						} else if (this.minFileSize.compareTo(this.fileThreshold) > 0) {
							ArrayList<OrderByElement> obe;

							ColumnFetcher cf = new ColumnFetcher(rightTable);
							onExpression.accept(cf);
							OrderByElement temp = new OrderByElement();
							Column lc = cf.getLeftCol();
							Column rc = cf.getRightCol();
							String lcs = lc.getColumnName();
							String rcs = rc.getColumnName();
							Table lt = cf.getLeftTab();
							Table rt = cf.getRightTab();
							String lts = lt.getAlias() == null ? lt.getName() : lt.getAlias();
							String rts = rt.getAlias() == null ? rt.getName() : rt.getAlias();
							if (!joinCol.containsKey(lts) || !lcs
									.equalsIgnoreCase(joinCol
											.get(lts))) {
								joinCol.put(lts, lcs);
								if(!joinCount.containsKey(lts)) {
									joinCount.put(lts, 1);
								} else {
									int tempCount = joinCount.get(lts);
									joinCount.put(lts, ++tempCount);
								}

								temp.setExpression(lc);
								obe = new ArrayList<OrderByElement>();
								obe.add(temp);
								addToPlan("[External Sort on :: "
										+ lts + " OrderBy :: "
										+ obe.toString() + "]");
								oper = new ExternalSort(oper, lts, obe,
										swapDir, joinCount.get(lts));
								firstTime = false;
							}
							//							printPlan();
							if (!joinCol.containsKey(rts)
									|| !rcs.equalsIgnoreCase(joinCol
											.get(rts))) {
								joinCol.put(rts, rcs);
								if(!joinCount.containsKey(rts)) {
									joinCount.put(rts, 1);
								} else {
									int tempCount = joinCount.get(rts);
									joinCount.put(rts, ++tempCount);
								}
								temp = new OrderByElement();
								temp.setExpression(rc);
								obe = new ArrayList<OrderByElement>();
								obe.add(temp);
								//								System.out.println("Entering for right external sort");
								addToPlan("[External Sort on :: "
										+ rts + " OrderBy :: "
										+ obe.toString() + "]");
								rightOper = new ExternalSort(rightOper, rts, obe,
										swapDir,joinCount.get(rts));
							}
							//							printPlan();
							addToPlan("[Sort Merge Join on :: " + joinedTables
									+ " and " + rightTable + " Expr :: "
									+ onExpression.toString() + "]");
							oper = new SortMergeJoinOperator(oper, rightOper,
									onExpression, rightTable);
							//							printPlan();
						} else {
							addToPlan("[Hybrid Hash Join on :: " + joinedTables
									+ " and " + rightTable + " Expr :: "
									+ onExpression.toString() + "]");
							oper = new HHJoinOperator(oper, rightOper,
									onExpression, rightTable);
						}
					}

				} else {
					oper = new BNLJoinOperator(oper, rightOper, onExpression);
					addToPlan("[Block Nested Join on :: " + joinedTables
							+ " and " + rightTable + " No Expression]");
				}
			}
		}
		//				printPlan();
		Expression fullWhereClause = null;
		if (onExpressionList != null && onExpressionList.size() != 0) {
			// System.out.println("Right only selection exists!!!!"+eList);
			fullWhereClause = onExpressionList.get(0);
			for (int i = 1; i < onExpressionList.size(); i++) {
				fullWhereClause = new AndExpression(fullWhereClause,
						onExpressionList.get(i));
			}
			// System.out.println(rightWhereClause);
		}
		if (otherList != null && otherList.size() != 0) {
			if (fullWhereClause == null) {
				fullWhereClause = otherList.get(0);
			} else {
				fullWhereClause = new AndExpression(fullWhereClause,
						otherList.get(0));
			}
			for (int i = 1; i < otherList.size(); i++) {
				fullWhereClause = new AndExpression(fullWhereClause,
						otherList.get(i));
			}
			// System.out.println(rightWhereClause);
		}
		if (fullWhereClause != null) {
			oper = new SelectionOperator(oper, fullWhereClause);
			addToPlan("[Selection on :: " + joinedTables + " Expr :: "
					+ fullWhereClause.toString() + "]");
		}

		
		//		printPlan();
		if (((PlainSelect) selectBody).getGroupByColumnReferences() != null) {
			// Group By computation
			PlainSelect select = (PlainSelect) selectBody;
			List<Column> groupbyList = select.getGroupByColumnReferences();
			oper = new GroupbyOperator(oper, projectStmt, groupbyList);
			addToPlan("[Group By on :: " + joinedTables + " Groupby :: "
					+ groupbyList.toString() + "]");
			addToPlan("[Projection on :: " + joinedTables + " Columns :: "
					+ projectStmt.toString() + "]");
		} else if (isFunction) {
			oper = new AggregateOperator(oper, projectStmt);
			addToPlan("[Aggregate on :: " + joinedTables + "]");
			addToPlan("[Projection on :: " + joinedTables + " Columns :: "
					+ projectStmt.toString() + "]");
		} else {
			// System.out.println("Entering projection");
			oper = new ProjectionOperator(oper, projectStmt);
			addToPlan("[Projection on :: " + joinedTables + " Columns :: "
					+ projectStmt.toString() + "]");
		}
		//		printPlan();
		if (orderbyElements != null) {
			// System.out.println("Entering ExternalSort");
			if (this.minFileSize.compareTo(this.fileThreshold) > 0) {
				addToPlan("[External Sort on :: " + joinedTables
						+ " OrderBy :: " + orderbyElements.toString() + "]");
				oper = new ExternalSort(oper, "masterExternal",
						orderbyElements, swapDir,1);
			} else {
				List<Datum[]> listDatum = new ArrayList<Datum[]>();
				Datum[] t = oper.readOneTuple();
				while (t != null) {
					listDatum.add(t);
					t = oper.readOneTuple();
				}
				oper = new OrderByOperator(orderbyElements, listDatum);
				addToPlan("[Normal Sort on :: " + joinedTables + " OrderBy :: "
						+ orderbyElements.toString() + "]");
			}

		}
//		printPlan();
		if (limit != null) {
			// System.out.println("Entering Limit");
			oper = new LimitOperator(oper, limit.getRowCount());
			addToPlan("[Limit on :: " + joinedTables + " Rows :: "
					+ limit.getRowCount() + "]");
		}
//		printPlan();
		// oper.resetTupleMapping();
		return oper;
	}

	// public void OrderBy(ArrayList<Datum[]> list) {
	// if (list == null)
	// return;
	// OrderByOperator obp = new OrderByOperator(orderbyElements, list);
	// obp.setListDatum(list);
	// if (orderbyElements != null) {
	// obp.sort();
	// }
	// obp.print();
	// }

	private void columnReducer() {
		//		System.out.println(masterTableMap);
		//		System.out.println(masterTableColTypeMap);
		//		System.out.println(sqlQuery);
		int k;
		String tempTable;
		ArrayList<Column> tempColList;
//		ArrayList<String> tempColTypeList;
		ArrayList<Integer> tempRemoveIndex;
//		ArrayList<String> newColTypeList;
//		ArrayList<Column> newColList;
		Column tempCol;
		Iterator ite;
		for (Map.Entry<String,  ArrayList<Column>> entry : masterTableMap.entrySet()) {
			tempTable = entry.getKey();
			tempColList = entry.getValue();
//			tempColTypeList = masterTableColTypeMap.get(tempTable);
			k=0;
//			newColList = new ArrayList<Column>();
//			newColTypeList = new ArrayList<String>();
			tempRemoveIndex = new ArrayList<Integer>();
			ite = tempColList.iterator();
			while(ite.hasNext()) {
				tempCol = (Column) ite.next();
				if(!(sqlQuery !=null & sqlQuery.contains(tempCol.getColumnName()))) {
					tempRemoveIndex.add(k);
				} else {
//					newColList.add(tempCol);
//					newColTypeList.add(tempColTypeList.get(k));
				}
				k++;
			}
//			tableMap.put(tempTable, newColList);
//			tableColTypeMap.put(tempTable, newColTypeList);	
			tableRemoveCols.put(tempTable, tempRemoveIndex);
		}
		//		System.out.println(tableMap);
		//		System.out.println(tableColTypeMap);
//				System.out.println("tableRemoveCols" + tableRemoveCols);

	}

	public void processTuples(Operator oper) {
		// OrderByOperator obp = new OrderByOperator(orderbyElements);
		Datum[] t = oper.readOneTuple();
		while (t != null) {
			// if (orderbyElements != null) {
			// obp.addTuple(t);
			// } else {
			printTuple(t);
			// }
			t = oper.readOneTuple();
		}
		// if (orderbyElements != null) {
		// obp.sort();
		// obp.print();
		// }
	}

	private void printGroupTuples(ArrayList<Datum[]> finalGroupbyArrayList) {
		// System.out
		// .println("------------PRINTING TUPLE FROM GROUPBY OPERATOR--------");
		for (Datum[] singleDatum : finalGroupbyArrayList) {
			printTuple(singleDatum);
		}
	}

	private void printTuple(Datum[] row) {
		Boolean first = true;
		if (row != null && row.length != 0) {
			for (Datum col : row) {
				if (!first)
					System.out.print("|" + col);
				else {
					System.out.print(col);
					first = false;
				}
			}
			System.out.println();
		}
	}

	public void setTableDirectory(String tableDir) {
		// System.out.println("Setting to "+tableDir);
		this.tableDir = tableDir;

	}

	public void setIndexDirectory(String indexDir) {
		// System.out.println("Setting to "+tableDir);
		this.indexDir = indexDir;

	}

	public void setFromItems(FromItem fromItem) {
		this.fromItem = fromItem;
		if(fromItem instanceof Table) {
			this.tblJoinStr.add(fromItem.toString());
		}
	}

	public void addColsTypeToTable(String table,
			ArrayList<String> columnTypeList) {
		if (masterTableColTypeMap.containsKey(table)) {
			masterTableColTypeMap.remove(table);
			masterTableColTypeMap.put(table, columnTypeList);
		} else {
			masterTableColTypeMap.put(table, columnTypeList);
		}

	}

	public void addColsTypeToTable(
			Map<String, ArrayList<String>> tableColTypeMap) {
		this.masterTableColTypeMap = tableColTypeMap;

	}

	public void addColsToTable(Table table, ArrayList<Column> columnNameList) {
		masterTableMap.put(table.getName().toLowerCase(), columnNameList);

	}

	public void addJoins(List joins) {
		this.tableJoins = (ArrayList) joins;
		if(this.tableJoins != null) {
			for(int i =0;i<tableJoins.size() ;i++) {
//				System.out.println(tableJoins.get(i).getClass());
				if(tableJoins.get(i) instanceof Join) {
					FromItem tempTab = ((Join)tableJoins.get(i)).getRightItem();
					if(tempTab instanceof Table) {
						Table tempTbl = (Table)tempTab;
						if(tempTbl.getAlias() != null) {
							this.tblJoinStr.add(tempTbl.getAlias());
						} else {
							this.tblJoinStr.add(tempTbl.getName());
						}
						
					}
					
				}
				
			}
		}
	}

	public void addOrderBy(List orderByElements) {
		this.orderbyElements = orderByElements;

	}

	public void setSwapDirectory(String swapDir) {
		this.swapDir = swapDir;

	}

	public void addLimit(Limit limit) {
		this.limit = limit;

	}

	public void resetParam() {
		projectStmt = null;
		whereClause = null;
		selectBody = null;
		fromItem = null;
		planPrint = new StringBuffer();
		orderbyElements = null;
		limit = null;
//		tableColTypeMap = null;
		tableRemoveCols = null;
//		tableMap = null;
		firstTime = true;
	}

	public void addFileSize(Long fileSizeComp) {
		//				System.out.println(fileSizeComp);
		//		11632
		this.minFileSize = fileSizeComp;

	}

	public void setSql(String sql) {
		this.sqlQuery = sql;

	}

	public void addColsToTable(Map<String, ArrayList<Column>> tableMap2) {
		masterTableMap = tableMap2;

	}

	public void addQueryColsTypeToTable(
			Map<String, ArrayList<String>> tableColTypeMap2) {
		this.masterTableColTypeMap =  tableColTypeMap2;

	}

	public void addQueryColsToTable(Map<String, ArrayList<Column>> tableMap2) {
		this.masterTableMap = tableMap2;

	}

	public void addQueryRemoveCols(
			Map<String, ArrayList<Integer>> tableRemoveCols2) {
		this.tableRemoveCols = tableRemoveCols2; 

	}

	public void setMetainfo(Map<String, List<String>> metaInfo) {
		this.metaInfo = metaInfo;
		
	}

	public void processDmlStmt(Map<String, List<Statement>> stmtMap) {
		dmlworker dml = new dmlworker(indexDir, stmtMap, metaInfo);
		dml.setTabCols(masterTableMap);
		dml.setTabColsType(masterTableColTypeMap);
		dml.processor();
		
	}

}
