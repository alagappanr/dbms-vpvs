package edu.buffalo.cse562.physicalPlan;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import edu.buffalo.cse562.sql.expression.evaluator.CalcTools;
import edu.buffalo.cse562.sql.expression.evaluator.ColumnFetcher;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.OrderByElement;

public class ExternalSort implements Operator {

	Operator oper;
	String swapDir;
	BufferedReader reader, reader1;
	Map<Integer, ArrayList<Datum[]>> buffer;
	Integer bufferMaxSize;
	// HashMap<Integer, ArrayList<Datum[]>> hmap;
	ArrayList<Datum[]> result = null;
	Integer resultIndex;
	ArrayList<ObjectInputStream> buffread = null;
	List<OrderByElement> elements;
	List<Integer> index;
	String masterFile;
	ObjectInputStream masterBuffer;
	String tableName;
	List<Boolean> asc;
	Boolean first;
	Integer kWay;
	Integer capacity;

	// Constructor of ExternalSort
	public ExternalSort(Operator oper, String tableName, List elements,
			String swapDir, Integer countTable) {
		this.oper = oper;
		this.elements = elements;
		this.swapDir = swapDir;
		this.bufferMaxSize = 100000;
		this.kWay = 5;
		this.capacity = 20000;
		this.first = true;
		this.tableName = tableName;
		this.resultIndex = 0;
		
		// System.out.println(this.swapDir);
		if (!(new File(this.swapDir).exists())) {
			this.swapDir = new File("").getAbsolutePath() + File.separator
					+ this.swapDir;
		}
		this.swapDir = this.swapDir + File.separator + tableName
				+ (countTable == null ? 0 : countTable) + "_";
		// File swapDirObj = new File(this.swapDir);
		// boolean result = swapDirObj.mkdirs();
		// if(!result) {
		// System.out.println("Parent Directories not created");
		// }

		// else {
		// boolean result = new File(this.swapDir).mkdirs();
		// if(!result) {
		// System.out.println("Parent Directories not created");
		//
		// }
		// }
		// System.out.println(this.swapDir);
		this.masterFile = this.swapDir + tableName + ".ser";

		// hmap = new HashMap<Integer, ArrayList<Datum[]>>();
		asc = new ArrayList<Boolean>();
		buffer = new HashMap<Integer, ArrayList<Datum[]>>();
		buffread = new ArrayList<ObjectInputStream>();
		sortfile();
	}

	public boolean readFile(ObjectInputStream br, int i) {
		try {

			// System.out.println("br.available :: "+br.available());
			ArrayList<Datum[]> datum = (ArrayList<Datum[]>) br.readObject();
			if (datum != null) {
				// System.out.println("Reading buffer from ");
				buffer.put(i, datum);
				return true;
			} else {
				// System.out.println("Closing Buffer");
				br.close();

			}
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;

	}

	public boolean createNewFile(String fileName) {
		try{
			File file = new File(fileName);
			if (!file.exists()) {
				return file.createNewFile();
			} else {
				file.delete();
				return file.createNewFile();
			}
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}

	}

	// Used to create a writer for an object stream
	public void writedata(ObjectOutputStream out, int i) {
		try {
			// System.out.println(out.toString());
			if (i < 0) {
				out.writeObject(result);
				out.reset();
				out.flush();				
			} else {
				out.writeObject(buffer.get(i));
				out.reset();
				out.flush();
			}
		} catch (IOException e) {
			e.printStackTrace();
		} 
	}

	// used to create a writer object stream
	public ObjectOutputStream writeOS(String fileName) {
		ObjectOutputStream out = null;
		try {
			out = new ObjectOutputStream(new FileOutputStream(fileName));
		} catch (IOException e) {
			e.printStackTrace();
		}
		return out;
	}

	// used to create an reader object stream
	public ObjectInputStream readOS(String fileName) {
		ObjectInputStream in = null;
		try {
			in = new ObjectInputStream(new FileInputStream(fileName));
		} catch (IOException e) {
			e.printStackTrace();
		}
		return in;
	}

	boolean readfile(int i, int depth) {
		Datum[] oneTupleFromDat = null;
		ArrayList<Datum[]> tempDatumList = new ArrayList<Datum[]>();
		int count = 0;
		oneTupleFromDat = oper.readOneTuple();
		// buffer = new ArrayList<Datum[]>();
		while (oneTupleFromDat != null) {
			tempDatumList.add(oneTupleFromDat);
			count++;
			if (count < bufferMaxSize) {
				oneTupleFromDat = oper.readOneTuple();
			} else {
				break;
			}
		}
		buffer.put(i, tempDatumList);
		if (buffer.get(i).size() > 0)
			return true;
		else {
			buffer.remove(i);
			return false;
		}
	}

	// This function return sort the buffer in first pass
	int readpage() {
		int i = 0;
		// int runs = 0;
		// String s1 = null;
		String s = null, index = null;
		// This function gets the first buffer

		try {
			while (readfile(i, 1)) {
				// System.out.println("Entering to sort Data :: ");
				sortdata(i);
				oper.resetTupleMapping();
				// System.out.println(swapDir);
				s = swapDir + "buffer[";
				index = Integer.toString(1) + Integer.toString(i);
				s = s + index + "].ser";
				ObjectOutputStream out = null;
				if(createNewFile(s)) {
					out = writeOS(s);
				} else {
					System.out.println("unable to create a file @ "+s);
				}
				writedata(out, i);
				buffer.remove(i);
				writedata(out, i);
				out.close();
				i++;
			}
			// System.out.println("printing tuples");
			// printTuple(buffer);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return i;
	}

	public void sortfile() {
		// Initial sort, completed phase 1
		int runs = readpage();
		if (runs == 1) {
			// System.out.println(swapDir);
			String s = swapDir + "buffer[10].ser";
			File buffername = new File(s);

			File file2 = new File(masterFile);
			if (!buffername.renameTo(file2)) {
				System.out.println("File renaming failed");
			}
			return;
		}
		if (runs == 0) {
			ObjectOutputStream out = null;
			if(createNewFile(masterFile)) {
				out = writeOS(masterFile);
			} else {
				System.out.println("unable to create a file @ "+masterFile);
			}
			try {
				out.writeObject(null);
				out.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
		int count = 0;
		String s = null;

		int i = 0, k = 0, runcurrent = 0, depth = 1, filenumber = 0;
		boolean check = true;
		while (i < runs && check) {
			// if(depth == 2)
			// kWay = 2;
			if (runs - i >= kWay) {
				k = kWay;
				if (runs - i - k < 3) {
					k = runs - i;
				}
			} else {
				k = runs - i;
			}

			count = k;
			while (count > 0) {

				s = swapDir + "buffer[";
				String index = Integer.toString(depth) + Integer.toString(i);
				s = s + index + "].ser";

				buffread.add(readOS(s));

				readFile(buffread.get(i), i);

				count--;
				i++;
			}

			runcurrent++;
//			System.out.println("depth :: " + depth + " k :: " + k);
			if (runcurrent == 1 && i == runs) {
				secondsort(i - k, k, filenumber, 0);
			} else {
				secondsort(i - k, k, filenumber, depth);
			}

			filenumber++;

			if (i == runs) {
				if (runcurrent == 1) {
					check = false;
					break;
				}
				runs = runcurrent;
				runcurrent = 0;
				filenumber = 0;
				depth = depth + 1;
				k = 0;
				i = 0;
				buffer = new HashMap<Integer, ArrayList<Datum[]>>();
				buffread = new ArrayList<ObjectInputStream>();
			}
		}
	}

	void secondsort(int start, int count, int filenumber, int depth) {

		ObjectOutputStream out = null;
		int counter = 1;
		int numberofemptylists = 0;

		int k = start;
		Datum[] lowest = null;
		ArrayList<Datum[]> list1 = null;
		Datum[] element;
		int removeindex = -1;
		boolean check = true;
		ArrayList<Integer> indexTraversal = new ArrayList<Integer>();
		for (k = start; k < (start + count); k++)
			indexTraversal.add(k);

		Iterator<Integer> itIndexTrav = indexTraversal.iterator();

		k =0;
		try {
			result = new ArrayList<Datum[]>();
			while (result.size() < capacity && check) {
				while (itIndexTrav.hasNext()) {
					
					k = itIndexTrav.next();
					// System.out.println("start :: "+start+" :: "+count);
					// System.out.println("k :: " + k);
					list1 = buffer.get(k);
					// System.out.print("list1 :: ");
					// printTuple(list1);
					if (list1 != null && list1.size() > 0) {
						element = list1.get(0);

						if (first) {
							TupleStruct.setTupleTableMap(element);
							computeIndex(element);

							first = false;
						}
						if (lowest == null) {
							lowest = element;
							removeindex = k;

						} else if (compare(element, lowest) <= 0) {
							lowest = element;
							removeindex = k;

						}
						
//						System.out.println("lowest selected :: ");
//						printTuple(lowest);
//						System.out.println("element compared :: ");
//						printTuple(element);
//						System.out.println(" removeindex :: "+ removeindex);
						
					} else {

						if (readFile(buffread.get(k), k)) {
							itIndexTrav = indexTraversal.iterator();							
							continue;
						} else {


							itIndexTrav.remove();
							if(buffer.get(k) != null && buffer.get(k).size() >= 0) {
//								System.out.println("removing buffer");
								buffer.remove(k);
							}
							numberofemptylists++;
							if (numberofemptylists == (count - 1)) {
								// System.out.println("writing to list");

								list1 = null;
								if (counter == 1) {
									String s = computeFile(depth, filenumber);
									//
									if(createNewFile(s)) {
										out = writeOS(s);
									} else {
										System.out.println("unable to create a file @ "+s);
									}
									//
									counter = 0;
								}
								if (result.size() > 0) {
									// System.out.print("result last :: ");
									// printTuple(result);
									// System.out.println();
									writedata(out, -2);
								}

								result = null;

								int m = indexTraversal.get(0);
								if (buffer.get(m).size() > 0) {
									// System.out.print("buffer last :: ");
									// printTuple(buffer.get(m));
									// System.out.println();
									writedata(out, m);
								}

//								System.out.println("temp buffer :: ");
//								printTuple(buffer);
//								System.out.println("temp result :: ");
//								printTuple(result);
//								System.out.println("temp buffread :: ");
//								System.out.println(buffread);

								while (readFile(buffread.get(m), m)) {
									// System.out.print("buffer last fin :: ");
									// printTuple(buffer.get(m));
									// System.out.println();
									writedata(out, m);

								}
								buffer.remove(m);
								// buffread = new
								// ArrayList<ObjectInputStream>();
								// break;

								// }

								check = false;
								// System.out.println("Changing check value :: "
								// + check);
								// list1 = null;
								indexTraversal = new ArrayList<Integer>();
								break;

							}

						}

					}
				}

				if (!check)
					break;

				result.add(lowest);
//				 System.out.println();
//				 System.out.print("lowest added :: ");
//				 printTuple(lowest);
//				 System.out.println();
//				 System.out.println(indexTraversal);
				if (removeindex != -1) {
					lowest = null;
					if (buffer != null && buffer.get(removeindex).size() > 0)
						buffer.get(removeindex).remove(0);
				} else {

					throw new Exception("Invalid Index in external sort"
							+ removeindex);
				}
				itIndexTrav = indexTraversal.iterator();
				if (result.size() == capacity) {

					if (counter == 1) {
						String s = computeFile(depth, filenumber);
						if(createNewFile(s)) {
							out = writeOS(s);
						} else {
							System.out.println("unable to create a file @ "+s);
						}
						counter = 0;
					}
					list1 = null;
					removeindex = -1;
//					System.out.println("comp buffer :: ");
//					printTuple(buffer);
//					System.out.println("comp result :: ");
//					printTuple(result);
//					System.out.println("comp buffread :: ");
//					System.out.println(buffread);

					writedata(out, -2);
					result = new ArrayList<Datum[]>();
				}
			}
			result = null;
			writedata(out, -2);
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}


	public int binarySearch(ArrayList<Datum[]> searchList, Datum[] searchDatum) {
		int low=0, high=0, mid=0;
		if (searchList == null || searchList.size() > 0){
			low = 0;
			high = searchList.size()-1;
			while(compare(searchList.get(low), searchList.get(high)) <= 0) {
				mid = low+high /2;
				if(compare(searchList.get(mid), searchDatum) > 0) {
					high = mid -1;
				} else if(compare(searchList.get(mid), searchDatum) < 0) {
					low = mid + 1;
				} else {
					return mid;
				}
			}
		}
		return low-1;
	}

	String computeFile(int depth, int i) {

		String s = null, index = null;
		if (depth == 0) {
			s = masterFile;
		} else {
			s = swapDir + "buffer[";
			index = Integer.toString(depth + 1) + Integer.toString(i);
			s = s + index + "].ser";
		}

		return s;
	}

	void sortdata(int i) {
		Collections.sort(buffer.get(i), new Mysorter(elements));
	}

	public void computeIndex(Datum[] tuple) {
		int ind;
		Column col;
		index = new ArrayList<Integer>();
		Iterator iter = elements.iterator();
		while (iter.hasNext()) {
			OrderByElement ele = (OrderByElement) iter.next();
			Expression exe = ele.getExpression();
			ColumnFetcher cf = new ColumnFetcher();
			exe.accept(cf);
			col = cf.getCol();
			if (col != null) {
				ind = TupleStruct.getColIndex(tuple, col);
				if (ind != -1) {
					index.add(ind);
					asc.add(ele.isAsc());
				} else {
					System.out
							.println("Index not fetched properly :: ExternalSort");
				}

			} else {
				System.out
						.println("Column not fetched properly :: ExternalSort");
			}

		}
	}

	public int compare(Datum[] t1, Datum[] t2) {
		int comparison = -2;
		int k = 0;
		int indexSize = index.size();
		int ind;
		while (k < indexSize) {
			ind = index.get(k);
			comparison = TupleStruct.getCompareValue((Object) t1[ind],
					(Object) t2[ind], asc.get(k));

			if (comparison != 0) {

				return comparison;
			}
			k++;
		}

		return comparison;

	}

	@Override
	public void resetStream() {
		oper.resetStream();
	}

	@Override
	public Datum[] readOneTuple() {
		Datum[] tuple = null;
		try {
			if (result == null) {
				// System.out.println("result is null");
				this.masterBuffer = readOS(masterFile);
				result = (ArrayList<Datum[]>) masterBuffer.readObject();
				if (result == null) {
					tuple = null;
				} else {
					tuple = result.get(resultIndex);
					++resultIndex;
				}
			} else {
				// System.out.println("reading result");
				if (resultIndex != result.size()) {
					tuple = result.get(resultIndex);
					++resultIndex;
				} else {
					// System.out.println("refilling result");
					result = (ArrayList<Datum[]>) masterBuffer.readObject();
					// printTuple(result);
					resultIndex = 0;
					if (result != null) {
						// System.out.println("re-reading result");
						tuple = result.get(resultIndex);
						++resultIndex;
					} else {
						// System.out.println("No more Tuples");
						masterBuffer.close();
						// File f = new File(swapDir);
						// for(File file: f.listFiles()) {
						// // System.out.println(file.toString());
						// file.delete();
						// }
						tuple = null;
					}
				}

			}
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		// System.out.println(masterFile + " :: External Sort :: ");
		// printTuple(tuple);
		// System.out.println();
		return tuple;
	}

	@Override
	public void resetTupleMapping() {

	}

	public void printTuple(Map<Integer, ArrayList<Datum[]>> hashTable) {
		Iterator it = hashTable.entrySet().iterator();
		ArrayList<Datum[]> tempList;
		while (it.hasNext()) {
			Map.Entry pairs = (Map.Entry) it.next();
			System.out.print(pairs.getKey() + " = ");
			tempList = (ArrayList<Datum[]>) pairs.getValue();
			Iterator it1 = tempList.iterator();
			while (it1.hasNext()) {
				printTuple((Datum[]) it1.next());
				System.out.print(",");
			}
			System.out.println();
		}
	}

	public void printTuple(Datum[] row) {
		Boolean first = true;
		if (row != null && row.length != 0) {
//			for (Datum col : row) {
//				if (!first)
//					System.out.print("|" + col);
//				else {
//					System.out.print(col);
//					first = false;
//				}
//			}
//			 System.out.println();
			System.out.print(row[0].toString());
		}
	}

	public void printTuple(ArrayList<Datum[]> row) {
		if (row != null) {
			Iterator ite = row.iterator();
			while (ite.hasNext()) {
				printTuple((Datum[]) ite.next());
				System.out.print(" , ");
			}
			System.out.println();
		}
	}

}