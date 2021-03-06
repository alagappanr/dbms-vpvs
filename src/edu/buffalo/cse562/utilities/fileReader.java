package edu.buffalo.cse562.utilities;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import edu.buffalo.cse562.exception.fileException;
import edu.buffalo.cse562.logger.logManager;

public class fileReader {

	String fileName;
	File file;
	List<String> contents;

	public fileReader(String fileName) {
		this.fileName = fileName;

		if (new File(fileName).exists()) {
			this.file = new File(fileName);
		} else {
			String basePath = new File("").getAbsolutePath();
			this.file = new File(basePath + File.separator + fileName);
		}
	}

	public fileReader(File fileName) {
		this.file = fileName;
	}

	public List<String> readContents() {
		contents = new ArrayList<String>();
		try {

			String line = null;
			Boolean normalQuery = false;
			Boolean tchpQuery = false;
			String lineAppend = "";
			String fileNm = fileName
					.substring(fileName.lastIndexOf(File.separator) + 1,
							fileName.length());
			if (fileNm.toLowerCase().startsWith("tpch") || fileNm.toLowerCase().startsWith("query")) {
				tchpQuery = true;
			} else {
				normalQuery = true;
			}
			BufferedReader buf = new BufferedReader(new FileReader(file));
			while ((line = buf.readLine()) != null) {

				if (line.length() == 0 && line.isEmpty())
					continue;
				else if (line.startsWith("--"))
					continue;
				else {
					if (line.contains("--") && (line.indexOf("--") > line.indexOf(";")))
						line = line.substring(0, line.indexOf("--"));
					if (normalQuery) {
						contents.add(line);
						normalQuery = true;
						continue;
					}
					if (tchpQuery) {
						if (!line.trim().endsWith(";")) {
							lineAppend += line + " ";
						} else {
							lineAppend += line + " ";
							contents.add(lineAppend);
							lineAppend = "";
						}
					}
				}

			}

			buf.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return contents;

	}

}
