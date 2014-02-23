package edu.buffalo.cse562.physicalPlan;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;

public class FileScanOperator implements Operator {
	private String dirName;
	private List<Column> tableMap;
	private Map<String, ArrayList<String>> tableColTypeMap;
	BufferedReader reader;
	Table tableName;
	String tablefile;

	public FileScanOperator(Table tableName, String dirName,
			List<Column> tableMap2,
			Map<String, ArrayList<String>> tableColTypeMap) {
		this.dirName = dirName;
		this.tableMap = tableMap2;
		this.tableName = tableName;
		this.tableColTypeMap = tableColTypeMap;
		String basePath = dirName + File.separator + tableName.getName() + ".dat";
		if(!(new File(basePath).exists())) {
			tablefile = new File("").getAbsolutePath() + File.separator + basePath;
		} else {
			tablefile = basePath;
		}
		resetStream();
	}

	/*
	 * @author - vino logic - construct tuple object with map containing key as
	 * column name (ArrayList<String>) and value as table data
	 * (ArrayList<String>)
	 */
	@Override
	public Datum[] readOneTuple() {
		if (reader == null) {
			System.out.println("Buffer not initialized for table ::"
					+ tableName);
			return null;
		}
		Datum[] oneTupleFromDat = null;
		try {
			String line = null;
			if ((line = reader.readLine()) != null) {
				if(line.equalsIgnoreCase("") || line.isEmpty()) {
					return null;
				}
				String[] singleTableElement = line.split("\\|");

				// oneTupleFromDat = new Tuple(convertType(singleTableElement));
				oneTupleFromDat = convertType(singleTableElement);
			} else {

				return null;
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return oneTupleFromDat;
	}

	public Datum[] convertType(String[] singleTableElement) {
		// Map tupleKeyValueMap = new HashMap();
		String key = null, value = null, type = null;
		Column col = null;
		Datum[] t = new Datum[singleTableElement.length];
		int i = 0;
		int j = 0;
		while (tableMap.size() != j && !tableMap.get(j).getTable().toString()
				.equalsIgnoreCase(tableName.getName().toString())) {
			j++;
		}
		while (i < singleTableElement.length) {
			col = tableMap.get(j);
			value = singleTableElement[i];
			type = tableColTypeMap.get(tableName.getName().toString().toLowerCase()).get(i).toLowerCase();
			if (type.equalsIgnoreCase("int")) {
				// tupleKeyValueMap.put(key, Integer.parseInt(value));
				t[i] = new Datum.dLong(singleTableElement[i], new Column(tableName,col.getColumnName()));
				//System.out.print(t[i].toComString());
			} else if (type.equalsIgnoreCase("decimal")) {
				// tupleKeyValueMap.put(key, Integer.parseInt(value));
				t[i] = new Datum.dDecimal(singleTableElement[i], new Column(tableName,col.getColumnName()));
				//System.out.print(t[i].toComString());
			} else if (type.equalsIgnoreCase("String") || type.startsWith("char") || type.startsWith("varchar")) {
				// tupleKeyValueMap.put(key, value);
				t[i] = new Datum.dString(singleTableElement[i], new Column(tableName,col.getColumnName()));
				//System.out.print(t[i].toComString());
			} else if (type.equalsIgnoreCase("date")) {
				// tupleKeyValueMap.put(key, (new SimpleDateFormat(
				// "YYYY-MM-DD", Locale.ENGLISH).parse(value)));
				t[i] = new Datum.dDate(singleTableElement[i], new Column(tableName,col.getColumnName()));
				//System.out.print(t[i].toComString());
			} else {
				try {
					throw new Exception("Not aware of this data type :: "
							+ type);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			i++; j++;
		}
		return t;

	}

	@Override
	public void resetStream() {
		try {
			reader = new BufferedReader(new FileReader(tablefile));
		} catch (IOException e) {

			e.printStackTrace();
			reader = null;
		}

	}
}
