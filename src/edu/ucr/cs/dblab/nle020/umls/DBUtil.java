package edu.ucr.cs.dblab.nle020.umls;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DBUtil {

	public static final String USERNAME = "root";
	public static final String PASSWORD = "root";
	public static final String DB_DRIVER = "com.mysql.jdbc.Driver";
	public static final String DB_URL = "jdbc:mysql://localhost:3306/";

	protected Connection connection = null;
	
	public Connection setConnection(String dbName) throws SQLException {
		try {
			connection = setConnection(DB_DRIVER, DB_URL + dbName + "?useUnicode=true&characterEncoding=UTF-8", USERNAME, PASSWORD);
			
		} catch (InstantiationException | IllegalAccessException
				| ClassNotFoundException e) {
			e.printStackTrace();
		}
		
		return connection;
	}
	
	public Connection setConnection(String dbName, String URL) throws SQLException {
		try {
			connection = setConnection(DB_DRIVER, "jdbc:mysql://" + URL + ":3306/" + dbName + "?useUnicode=true&characterEncoding=UTF-8", USERNAME, PASSWORD);
			
		} catch (InstantiationException | IllegalAccessException
				| ClassNotFoundException e) {
			e.printStackTrace();
		}
		
		return connection;
	}
	
	public Connection setConnection(String driver, String urlWithDBName, String user, String password)
			throws InstantiationException, IllegalAccessException, ClassNotFoundException, SQLException {
		Class.forName(DB_DRIVER).newInstance();
		connection = DriverManager.getConnection(urlWithDBName, user, password);
		
		Statement stm = connection.createStatement();
		stm.execute("SET NAMES 'utf8'");
		stm.close();
		
		return connection;
	}
	
	
	public void closeAll() {
		if (connection != null) {
			try {
				connection.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
	
	public Connection getConnection() {
		return connection;
	}
}
