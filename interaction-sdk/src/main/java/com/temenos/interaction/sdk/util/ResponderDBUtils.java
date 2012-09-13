package com.temenos.interaction.sdk.util;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Query;

/**
 * Utility class to read SQL insert statement froma file and 
 * inject them into a database. 
 */
public class ResponderDBUtils {
	private final static Logger logger = Logger.getLogger(ResponderDBUtils.class.getName());

	public static String fillDatabase(EntityManagerFactory entityManagerFactory) {
		logger.fine("Creating an entity manager");
		EntityManager entityManager = entityManagerFactory.createEntityManager();

		String line = "";
		try {
			logger.fine("Loading SQL INSERTs file");
			InputStream xml = ResponderDBUtils.class.getResourceAsStream("/META-INF/responder_insert.sql");
			if (xml == null){
				return "ERROR: DML file not found [/META-INF/responder_insert.sql].";
			}
			BufferedReader br = new BufferedReader(new InputStreamReader(xml, "UTF-8"));

			logger.fine("Reading SQL INSERTs file");
			int count = 0;
			while ((line = br.readLine()) != null) {
				if (!line.startsWith("#")) {
					line = line.replace("`", "");
					line = line.replace(");", ")");
					line = line.replace("'0x", "'");

					if (line.length() > 5) {
						logger.fine("Inserting record: " + line);
						Query query = entityManager.createNamedQuery(line);
						query.executeUpdate();
						count++;
					}
				}
			}

			br.close();
			logger.info(count + " rows have been inserted into the database.");

		} catch (Exception ex) {
			logger.severe("Failed to insert SQL statements.");
			ex.printStackTrace();
		} finally {
			entityManager.close();
		}
		return "OK";
	}

	public static void writeStringToFile(String fileName, String contents) {
		Writer out = null;
		try {
			out = new OutputStreamWriter(new FileOutputStream(fileName),
					"utf-8");
			out.write(contents);
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			if (out != null) {
				try {
					out.close();
				} catch (IOException e) {
				}
			}
		}

	}

	public static String readFileToString(String fileName) {
		return readFileToString(fileName, Charset.defaultCharset().name());
	}

	public static String readFileToString(String fileName, String charsetName) {
		StringBuilder strBuilder = new StringBuilder();
		try {
			InputStream buf = ResponderDBUtils.class
					.getResourceAsStream(fileName);

			BufferedReader in = new BufferedReader(new InputStreamReader(buf,
					charsetName));

			String str;

			try {
				while ((str = in.readLine()) != null) {
					strBuilder.append(str);
				}
				in.close();

			} catch (IOException ex) {
				Logger.getLogger(ResponderDBUtils.class.getName()).log(
						Level.SEVERE, null, ex);
			}

		} catch (Exception ex) {
			Logger.getLogger(ResponderDBUtils.class.getName()).log(
					Level.SEVERE, null, ex);
		}

		return strBuilder.toString();
	}

}
