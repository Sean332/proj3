package cpsc4620;

import java.io.IOException;
import java.sql.*;
import java.util.*;

/*
 * This file is where you will implement the methods needed to support this application.
 * You will write the code to retrieve and save information to the database and use that
 * information to build the various objects required by the applicaiton.
 * 
 * The class has several hard coded static variables used for the connection, you will need to
 * change those to your connection information
 * 
 * This class also has static string variables for pickup, delivery and dine-in. 
 * DO NOT change these constant values.
 * 
 * You can add any helper methods you need, but you must implement all the methods
 * in this class and use them to complete the project.  The autograder will rely on
 * these methods being implemented, so do not delete them or alter their method
 * signatures.
 * 
 * Make sure you properly open and close your DB connections in any method that
 * requires access to the DB.
 * Use the connect_to_db below to open your connection in DBConnector.
 * What is opened must be closed!
 */

/*
 * A utility class to help add and retrieve information from the database
 */

public final class DBNinja {
	private static Connection conn;

	// DO NOT change these variables!
	public final static String pickup = "pickup";
	public final static String delivery = "delivery";
	public final static String dine_in = "dinein";

	public final static String size_s = "Small";
	public final static String size_m = "Medium";
	public final static String size_l = "Large";
	public final static String size_xl = "XLarge";

	public final static String crust_thin = "Thin";
	public final static String crust_orig = "Original";
	public final static String crust_pan = "Pan";
	public final static String crust_gf = "Gluten-Free";

	public enum order_state {
		PREPARED,
		DELIVERED,
		PICKEDUP
	}


	private static boolean connect_to_db() throws SQLException, IOException 
	{

		try {
			conn = DBConnector.make_connection();
			return true;
		} catch (SQLException e) {
			return false;
		} catch (IOException e) {
			return false;
		}

	}

	public static void addOrder(Order o) throws SQLException, IOException 
	{
		/*
		 * add code to add the order to the DB. Remember that we're not just
		 * adding the order to the order DB table, but we're also recording
		 * the necessary data for the delivery, dinein, pickup, pizzas, toppings
		 * on pizzas, order discounts and pizza discounts.
		 * 
		 * This is a KEY method as it must store all the data in the Order object
		 * in the database and make sure all the tables are correctly linked.
		 * 
		 * Remember, if the order is for Dine In, there is no customer...
		 * so the cusomter id coming from the Order object will be -1.
		 * 
		 */
		connect_to_db();
		try {
			conn.setAutoCommit(false); // Enable transaction

			// Insert into ordertable
			String addOrderQuery = "INSERT INTO ordertable (customer_CustID, ordertable_OrderType, ordertable_OrderDateTime, " +
					"ordertable_CustPrice, ordertable_BusPrice, ordertable_IsComplete) VALUES (?, ?, ?, ?, ?, ?)";
			PreparedStatement addOrderStmt = conn.prepareStatement(addOrderQuery, Statement.RETURN_GENERATED_KEYS);

			addOrderStmt.setObject(1, (o.getCustID() == -1 ? null : o.getCustID())); // null for dine-in orders
			addOrderStmt.setString(2, o.getOrderType());
			addOrderStmt.setString(3, o.getDate());
			addOrderStmt.setDouble(4, o.getCustPrice());
			addOrderStmt.setDouble(5, o.getBusPrice());
			addOrderStmt.setBoolean(6, o.getIsComplete());

			addOrderStmt.executeUpdate();

			ResultSet rs = addOrderStmt.getGeneratedKeys();
			int orderID = -1;
			if (rs.next()) {
				orderID = rs.getInt(1);
			}

			// Add to subtype tables if needed
			if (o instanceof DineinOrder) {
				String dineinQuery = "INSERT INTO dinein (ordertable_OrderID, dinein_TableNum) VALUES (?, ?)";
				PreparedStatement dineinStmt = conn.prepareStatement(dineinQuery);
				dineinStmt.setInt(1, orderID);
				dineinStmt.setInt(2, ((DineinOrder) o).getTableNum());
				dineinStmt.executeUpdate();
			} else if (o instanceof PickupOrder) {
				String pickupQuery = "INSERT INTO pickup (ordertable_OrderID, pickup_IsPickedUp) VALUES (?, ?)";
				PreparedStatement pickupStmt = conn.prepareStatement(pickupQuery);
				pickupStmt.setInt(1, orderID);
				pickupStmt.setBoolean(2, ((PickupOrder) o).getIsPickedUp());
				pickupStmt.executeUpdate();
			} else if (o instanceof DeliveryOrder) {
				String deliveryQuery = "INSERT INTO delivery (ordertable_OrderID, delivery_HouseNum, delivery_Street, delivery_City, delivery_State, delivery_Zip, delivery_IsDelivered) " +
						"VALUES (?, ?, ?, ?, ?, ?, ?)";
				PreparedStatement deliveryStmt = conn.prepareStatement(deliveryQuery);
				DeliveryOrder deliveryOrder = (DeliveryOrder) o;
				String[] addressParts = deliveryOrder.getAddress().split("\t");
				deliveryStmt.setInt(1, orderID);
				deliveryStmt.setInt(2, Integer.parseInt(addressParts[0])); // HouseNum
				deliveryStmt.setString(3, addressParts[1]); // Street
				deliveryStmt.setString(4, addressParts[2]); // City
				deliveryStmt.setString(5, addressParts[3]); // State
				deliveryStmt.setInt(6, Integer.parseInt(addressParts[4])); // Zip
				deliveryStmt.setBoolean(7, deliveryOrder.getIsDelivered());
				deliveryStmt.executeUpdate();
			}

			// Add pizzas
			for (Pizza p : o.getPizzaList()) {
				addPizza(new java.util.Date(), orderID, p);
			}

			// Add order discounts
			for (Discount d : o.getDiscountList()) {
				String orderDiscountQuery = "INSERT INTO order_discount (ordertable_OrderID, discount_DiscountID) VALUES (?, ?)";
				PreparedStatement orderDiscountStmt = conn.prepareStatement(orderDiscountQuery);
				orderDiscountStmt.setInt(1, orderID);
				orderDiscountStmt.setInt(2, d.getDiscountID());
				orderDiscountStmt.executeUpdate();
			}

			conn.commit(); // Commit transaction
		} catch (SQLException e) {
			conn.rollback(); // Rollback on failure
			e.printStackTrace();
			throw e;
		} finally {
			conn.setAutoCommit(true); // Reset autocommit
			conn.close();
		}
	}
	
	public static int addPizza(java.util.Date d, int orderID, Pizza p) throws SQLException, IOException
	{
		/*
		 * Add the code needed to insert the pizza into into the database.
		 * Keep in mind you must also add the pizza discounts and toppings 
		 * associated with the pizza.
		 * 
		 * NOTE: there is a Date object passed into this method so that the Order
		 * and ALL its Pizzas can be assigned the same DTS.
		 * 
		 * This method returns the id of the pizza just added.
		 * 
		 */
		connect_to_db();
		int pizzaID = -1;
		try {
			conn.setAutoCommit(false);

			// Insert into pizza table
			String addPizzaQuery = "INSERT INTO pizza (pizza_Size, pizza_CrustType, pizza_PizzaDate, pizza_CustPrice, pizza_BusPrice, ordertable_OrderID) " +
					"VALUES (?, ?, ?, ?, ?, ?)";
			PreparedStatement addPizzaStmt = conn.prepareStatement(addPizzaQuery, Statement.RETURN_GENERATED_KEYS);

			addPizzaStmt.setString(1, p.getSize());
			addPizzaStmt.setString(2, p.getCrustType());
			addPizzaStmt.setTimestamp(3, new java.sql.Timestamp(d.getTime()));
			addPizzaStmt.setDouble(4, p.getCustPrice());
			addPizzaStmt.setDouble(5, p.getBusPrice());
			addPizzaStmt.setInt(6, orderID);

			addPizzaStmt.executeUpdate();

			ResultSet rs = addPizzaStmt.getGeneratedKeys();
			if (rs.next()) {
				pizzaID = rs.getInt(1);
			}

			// Add toppings
			for (Topping t : p.getToppings()) {
				String toppingQuery = "INSERT INTO pizza_topping (pizza_PizzaID, topping_TopID, pizza_topping_IsDouble) VALUES (?, ?, ?)";
				PreparedStatement toppingStmt = conn.prepareStatement(toppingQuery);
				toppingStmt.setInt(1, pizzaID);
				toppingStmt.setInt(2, t.getTopID());
				toppingStmt.setBoolean(3, t.getDoubled());
				toppingStmt.executeUpdate();

				// Update inventory
				String inventoryQuery = "UPDATE topping SET topping_CurINVT = topping_CurINVT - ? WHERE topping_TopID = ?";
				PreparedStatement inventoryStmt = conn.prepareStatement(inventoryQuery);
				double unitsNeeded = switch (p.getSize()) {
					case DBNinja.size_s -> t.getSmallAMT();
					case DBNinja.size_m -> t.getMedAMT();
					case DBNinja.size_l -> t.getLgAMT();
					case DBNinja.size_xl -> t.getXLAMT();
					default -> 0;
				};
				inventoryStmt.setDouble(1, unitsNeeded * (t.getDoubled() ? 2 : 1));
				inventoryStmt.setInt(2, t.getTopID());
				inventoryStmt.executeUpdate();
			}

			// Add discounts
			for (Discount d : p.getDiscounts()) {
				String pizzaDiscountQuery = "INSERT INTO pizza_discount (pizza_PizzaID, discount_DiscountID) VALUES (?, ?)";
				PreparedStatement pizzaDiscountStmt = conn.prepareStatement(pizzaDiscountQuery);
				pizzaDiscountStmt.setInt(1, pizzaID);
				pizzaDiscountStmt.setInt(2, d.getDiscountID());
				pizzaDiscountStmt.executeUpdate();
			}

			conn.commit();
		} catch (SQLException e) {
			conn.rollback();
			e.printStackTrace();
			throw e;
		} finally {
			conn.setAutoCommit(true);
			conn.close();
		}

		return -1;
	}
	
	public static int addCustomer(Customer c) throws SQLException, IOException
	 {
		/*
		 * This method adds a new customer to the database.
		 * 
		 */
		 connect_to_db();
		 int customerID = -1;
		 try {
			 // Insert customer into the customer table
			 String addCustomerQuery = "INSERT INTO customer (customer_FName, customer_LName, customer_PhoneNum) VALUES (?, ?, ?)";
			 PreparedStatement addCustomerStmt = conn.prepareStatement(addCustomerQuery, Statement.RETURN_GENERATED_KEYS);

			 addCustomerStmt.setString(1, c.getFName());
			 addCustomerStmt.setString(2, c.getLName());
			 addCustomerStmt.setString(3, c.getPhone());

			 addCustomerStmt.executeUpdate();

			 // Retrieve the generated customer ID
			 ResultSet rs = addCustomerStmt.getGeneratedKeys();
			 if (rs.next()) {
				 customerID = rs.getInt(1);
			 }
		 } catch (SQLException e) {
			 e.printStackTrace();
			 throw e;
		 } finally {
			 conn.close();
		 }
		 return customerID;
	}

	public static void completeOrder(int OrderID, order_state newState ) throws SQLException, IOException
	{
		/*
		 * Mark that order as complete in the database.
		 * Note: if an order is complete, this means all the pizzas are complete as well.
		 * However, it does not mean that the order has been delivered or picked up!
		 *
		 * For newState = PREPARED: mark the order and all associated pizza's as completed
		 * For newState = DELIVERED: mark the delivery status
		 * FOR newState = PICKEDUP: mark the pickup status
		 * 
		 */
		connect_to_db();
		try {
			conn.setAutoCommit(false);

			// Update order as complete
			if (newState == order_state.PREPARED) {
				String completeOrderQuery = "UPDATE ordertable SET ordertable_IsComplete = 1 WHERE ordertable_OrderID = ?";
				PreparedStatement completeOrderStmt = conn.prepareStatement(completeOrderQuery);
				completeOrderStmt.setInt(1, OrderID);
				completeOrderStmt.executeUpdate();

				// Mark all pizzas in the order as completed
				String completePizzaQuery = "UPDATE pizza SET pizza_PizzaState = 'completed' WHERE ordertable_OrderID = ?";
				PreparedStatement completePizzaStmt = conn.prepareStatement(completePizzaQuery);
				completePizzaStmt.setInt(1, OrderID);
				completePizzaStmt.executeUpdate();
			}

			// Update for PICKEDUP or DELIVERED status if needed
			if (newState == order_state.PICKEDUP) {
				String pickupUpdateQuery = "UPDATE pickup SET pickup_IsPickedUp = 1 WHERE ordertable_OrderID = ?";
				PreparedStatement pickupUpdateStmt = conn.prepareStatement(pickupUpdateQuery);
				pickupUpdateStmt.setInt(1, OrderID);
				pickupUpdateStmt.executeUpdate();
			} else if (newState == order_state.DELIVERED) {
				String deliveryUpdateQuery = "UPDATE delivery SET delivery_IsDelivered = 1 WHERE ordertable_OrderID = ?";
				PreparedStatement deliveryUpdateStmt = conn.prepareStatement(deliveryUpdateQuery);
				deliveryUpdateStmt.setInt(1, OrderID);
				deliveryUpdateStmt.executeUpdate();
			}

			conn.commit();
		} catch (SQLException e) {
			conn.rollback();
			e.printStackTrace();
			throw e;
		} finally {
			conn.setAutoCommit(true);
			conn.close();
		}
	}


	public static ArrayList<Order> getOrders(int status) throws SQLException, IOException
	 {
	/*
	 * Return an ArrayList of orders.
	 * 	status   == 1 => return a list of open (ie oder is not completed)
	 *           == 2 => return a list of completed orders (ie order is complete)
	 *           == 3 => return a list of all the orders
	 * Remember that in Java, we account for supertypes and subtypes
	 * which means that when we create an arrayList of orders, that really
	 * means we have an arrayList of dineinOrders, deliveryOrders, and pickupOrders.
	 *
	 * You must fully populate the Order object, this includes order discounts,
	 * and pizzas along with the toppings and discounts associated with them.
	 * 
	 * Don't forget to order the data coming from the database appropriately.
	 *
	 */
		 connect_to_db();
		 ArrayList<Order> orders = new ArrayList<>();
		 try {
			 String query;
			 if (status == 1) { // Open orders
				 query = "SELECT * FROM ordertable WHERE ordertable_IsComplete = 0 ORDER BY ordertable_OrderDateTime ASC";
			 } else if (status == 2) { // Completed orders
				 query = "SELECT * FROM ordertable WHERE ordertable_IsComplete = 1 ORDER BY ordertable_OrderDateTime ASC";
			 } else { // All orders
				 query = "SELECT * FROM ordertable ORDER BY ordertable_OrderDateTime ASC";
			 }

			 Statement stmt = conn.createStatement();
			 ResultSet rs = stmt.executeQuery(query);

			 while (rs.next()) {
				 int orderID = rs.getInt("ordertable_OrderID");
				 int custID = rs.getInt("customer_CustID");
				 String orderType = rs.getString("ordertable_OrderType");
				 String orderDate = rs.getString("ordertable_OrderDateTime");
				 double custPrice = rs.getDouble("ordertable_CustPrice");
				 double busPrice = rs.getDouble("ordertable_BusPrice");
				 boolean isComplete = rs.getBoolean("ordertable_IsComplete");

				 // Build the appropriate order type
				 Order order;
				 if (orderType.equals(DBNinja.dine_in)) {
					 String tableQuery = "SELECT dinein_TableNum FROM dinein WHERE ordertable_OrderID = ?";
					 PreparedStatement tableStmt = conn.prepareStatement(tableQuery);
					 tableStmt.setInt(1, orderID);
					 ResultSet tableRs = tableStmt.executeQuery();
					 int tableNum = tableRs.next() ? tableRs.getInt("dinein_TableNum") : -1;
					 order = new DineinOrder(orderID, custID, orderDate, custPrice, busPrice, isComplete, tableNum);
				 } else if (orderType.equals(DBNinja.pickup)) {
					 String pickupQuery = "SELECT pickup_IsPickedUp FROM pickup WHERE ordertable_OrderID = ?";
					 PreparedStatement pickupStmt = conn.prepareStatement(pickupQuery);
					 pickupStmt.setInt(1, orderID);
					 ResultSet pickupRs = pickupStmt.executeQuery();
					 boolean isPickedUp = pickupRs.next() && pickupRs.getBoolean("pickup_IsPickedUp");
					 order = new PickupOrder(orderID, custID, orderDate, custPrice, busPrice, isPickedUp, isComplete);
				 } else { // Delivery
					 String deliveryQuery = "SELECT delivery_HouseNum, delivery_Street, delivery_City, delivery_State, delivery_Zip, delivery_IsDelivered " +
							 "FROM delivery WHERE ordertable_OrderID = ?";
					 PreparedStatement deliveryStmt = conn.prepareStatement(deliveryQuery);
					 deliveryStmt.setInt(1, orderID);
					 ResultSet deliveryRs = deliveryStmt.executeQuery();
					 if (deliveryRs.next()) {
						 String address = deliveryRs.getInt("delivery_HouseNum") + "\t" +
								 deliveryRs.getString("delivery_Street") + "\t" +
								 deliveryRs.getString("delivery_City") + "\t" +
								 deliveryRs.getString("delivery_State") + "\t" +
								 deliveryRs.getInt("delivery_Zip");
						 boolean isDelivered = deliveryRs.getBoolean("delivery_IsDelivered");
						 order = new DeliveryOrder(orderID, custID, orderDate, custPrice, busPrice, isComplete, address, isDelivered);
					 } else {
						 throw new SQLException("Delivery details missing for order ID " + orderID);
					 }
				 }

				 // Fetch pizzas and discounts for this order
				 order.setPizzaList(getPizzas(order));
				 order.setDiscountList(getDiscounts(order));

				 orders.add(order);
			 }
		 } catch (SQLException e) {
			 e.printStackTrace();
			 throw e;
		 } finally {
			 conn.close();
		 }
		 return orders;
	}
	
	public static Order getLastOrder() throws SQLException, IOException 
	{
		/*
		 * Query the database for the LAST order added
		 * then return an Order object for that order.
		 * NOTE...there will ALWAYS be a "last order"!
		 */
		 return null;
	}

	public static ArrayList<Order> getOrdersByDate(String date) throws SQLException, IOException
	 {
		/*
		 * Query the database for ALL the orders placed on a specific date
		 * and return a list of those orders.
		 *  
		 */
		 return null;
	}
		
	public static ArrayList<Discount> getDiscountList() throws SQLException, IOException 
	{
		/* 
		 * Query the database for all the available discounts and 
		 * return them in an arrayList of discounts ordered by discount name.
		 * 
		*/
		return null;
	}

	public static Discount findDiscountByName(String name) throws SQLException, IOException 
	{
		/*
		 * Query the database for a discount using it's name.
		 * If found, then return an OrderDiscount object for the discount.
		 * If it's not found....then return null
		 *  
		 */
		 return null;
	}


	public static ArrayList<Customer> getCustomerList() throws SQLException, IOException 
	{
		/*
		 * Query the data for all the customers and return an arrayList of all the customers. 
		 * Don't forget to order the data coming from the database appropriately.
		 * 
		*/
		return null;
	}

	public static Customer findCustomerByPhone(String phoneNumber)  throws SQLException, IOException 
	{
		/*
		 * Query the database for a customer using a phone number.
		 * If found, then return a Customer object for the customer.
		 * If it's not found....then return null
		 *  
		 */
		 return null;
	}

	public static String getCustomerName(int CustID) throws SQLException, IOException 
	{
		/*
		 * COMPLETED...WORKING Example!
		 * 
		 * This is a helper method to fetch and format the name of a customer
		 * based on a customer ID. This is an example of how to interact with
		 * your database from Java.  
		 * 
		 * Notice how the connection to the DB made at the start of the 
		 *
		 */

		 connect_to_db();

		/* 
		 * an example query using a constructed string...
		 * remember, this style of query construction could be subject to sql injection attacks!
		 * 
		 */
		String cname1 = "";
		String cname2 = "";
		String query = "Select customer_FName, customer_LName From customer WHERE customer_CustID=" + CustID + ";";
		Statement stmt = conn.createStatement();
		ResultSet rset = stmt.executeQuery(query);
		
		while(rset.next())
		{
			cname1 = rset.getString(1) + " " + rset.getString(2); 
		}

		/* 
		* an BETTER example of the same query using a prepared statement...
		* with exception handling
		* 
		*/
		try {
			PreparedStatement os;
			ResultSet rset2;
			String query2;
			query2 = "Select customer_FName, customer_LName From customer WHERE customer_CustID=?;";
			os = conn.prepareStatement(query2);
			os.setInt(1, CustID);
			rset2 = os.executeQuery();
			while(rset2.next())
			{
				cname2 = rset2.getString("customer_FName") + " " + rset2.getString("customer_LName"); // note the use of field names in the getSting methods
			}
		} catch (SQLException e) {
			e.printStackTrace();
			// process the error or re-raise the exception to a higher level
		}

		conn.close();

		return cname1;
		// OR
		// return cname2;

	}


	public static ArrayList<Topping> getToppingList() throws SQLException, IOException 
	{
		/*
		 * Query the database for the aviable toppings and 
		 * return an arrayList of all the available toppings. 
		 * Don't forget to order the data coming from the database appropriately.
		 * 
		 */
		return null;
	}

	public static Topping findToppingByName(String name) throws SQLException, IOException 
	{
		/*
		 * Query the database for the topping using it's name.
		 * If found, then return a Topping object for the topping.
		 * If it's not found....then return null
		 *  
		 */
		 return null;
	}

	public static ArrayList<Topping> getToppingsOnPizza(Pizza p) throws SQLException, IOException 
	{
		/* 
		 * This method builds an ArrayList of the toppings ON a pizza.
		 * The list can then be added to the Pizza object elsewhere in the
		 */

		return null;	
	}

	public static void addToInventory(int toppingID, double quantity) throws SQLException, IOException 
	{
		/*
		 * Updates the quantity of the topping in the database by the amount specified.
		 * 
		 * */
	}
	
	
	public static ArrayList<Pizza> getPizzas(Order o) throws SQLException, IOException 
	{
		/*
		 * Build an ArrayList of all the Pizzas associated with the Order.
		 * 
		 */
		connect_to_db();
		ArrayList<Pizza> pizzas = new ArrayList<>();
		try {
			String pizzaQuery = "SELECT * FROM pizza WHERE ordertable_OrderID = ?";
			PreparedStatement pizzaStmt = conn.prepareStatement(pizzaQuery);
			pizzaStmt.setInt(1, o.getOrderID());
			ResultSet rs = pizzaStmt.executeQuery();

			while (rs.next()) {
				int pizzaID = rs.getInt("pizza_PizzaID");
				String size = rs.getString("pizza_Size");
				String crustType = rs.getString("pizza_CrustType");
				String pizzaState = rs.getString("pizza_PizzaState");
				String pizzaDate = rs.getString("pizza_PizzaDate");
				double custPrice = rs.getDouble("pizza_CustPrice");
				double busPrice = rs.getDouble("pizza_BusPrice");

				Pizza pizza = new Pizza(pizzaID, size, crustType, o.getOrderID(), pizzaState, pizzaDate, custPrice, busPrice);

				// Fetch toppings for this pizza
				ArrayList<Topping> toppings = getToppingsOnPizza(pizza);
				pizza.setToppings(toppings);

				// Fetch discounts for this pizza
				ArrayList<Discount> discounts = getDiscounts(pizza);
				pizza.setDiscounts(discounts);

				pizzas.add(pizza);
			}
		} catch (SQLException e) {
			e.printStackTrace();
			throw e;
		} finally {
			conn.close();
		}
		return pizzas;
	}

	public static ArrayList<Discount> getDiscounts(Order o) throws SQLException, IOException 
	{
		/* 
		 * Build an array list of all the Discounts associted with the Order.
		 * 
		 */
		connect_to_db();
		ArrayList<Discount> discounts = new ArrayList<>();
		try {
			String discountQuery = "SELECT d.* FROM discount d " +
					"JOIN order_discount od ON d.discount_DiscountID = od.discount_DiscountID " +
					"WHERE od.ordertable_OrderID = ?";
			PreparedStatement discountStmt = conn.prepareStatement(discountQuery);
			discountStmt.setInt(1, o.getOrderID());
			ResultSet rs = discountStmt.executeQuery();

			while (rs.next()) {
				int discountID = rs.getInt("discount_DiscountID");
				String discountName = rs.getString("discount_DiscountName");
				double amount = rs.getDouble("discount_Amount");
				boolean isPercent = rs.getBoolean("discount_IsPercent");

				discounts.add(new Discount(discountID, discountName, amount, isPercent));
			}
		} catch (SQLException e) {
			e.printStackTrace();
			throw e;
		} finally {
			conn.close();
		}
		return discounts;
	}

	public static ArrayList<Discount> getDiscounts(Pizza p) throws SQLException, IOException 
	{
		/* 
		 * Build an array list of all the Discounts associted with the Pizza.
		 * 
		 */
		connect_to_db();
		ArrayList<Discount> discounts = new ArrayList<>();
		try {
			String discountQuery = "SELECT d.* FROM discount d " +
					"JOIN pizza_discount pd ON d.discount_DiscountID = pd.discount_DiscountID " +
					"WHERE pd.pizza_PizzaID = ?";
			PreparedStatement discountStmt = conn.prepareStatement(discountQuery);
			discountStmt.setInt(1, p.getPizzaID());
			ResultSet rs = discountStmt.executeQuery();

			while (rs.next()) {
				int discountID = rs.getInt("discount_DiscountID");
				String discountName = rs.getString("discount_DiscountName");
				double amount = rs.getDouble("discount_Amount");
				boolean isPercent = rs.getBoolean("discount_IsPercent");

				discounts.add(new Discount(discountID, discountName, amount, isPercent));
			}
		} catch (SQLException e) {
			e.printStackTrace();
			throw e;
		} finally {
			conn.close();
		}
		return discounts;
	}

	public static double getBaseCustPrice(String size, String crust) throws SQLException, IOException 
	{
		/* 
		 * Query the database fro the base customer price for that size and crust pizza.
		 * 
		*/
		connect_to_db();
		try {

			String query = "SELECT base_price FROM pizza_prices WHERE size = ? AND crust = ?";
			PreparedStatement stmt = conn.prepareStatement(query);
			stmt.setString(1, size);
			stmt.setString(2, crust);
			ResultSet rs = stmt.executeQuery();

			if (rs.next()) {
				return rs.getDouble("base_price");
			}
		} catch (SQLException e) {
			e.printStackTrace();
			throw e;
		} finally {
			conn.close();
		}
		return 0.0;
	}

	public static double getBaseBusPrice(String size, String crust) throws SQLException, IOException 
	{
		/* 
		 * Query the database fro the base business price for that size and crust pizza.
		 * 
		*/
		return 0.0;
	}

	
	public static void printToppingPopReport() throws SQLException, IOException
	{
		/*
		 * Prints the ToppingPopularity view. Remember that this view
		 * needs to exist in your DB, so be sure you've run your createViews.sql
		 * files on your testing DB if you haven't already.
		 * 
		 * The result should be readable and sorted as indicated in the prompt.
		 * 
		 * HINT: You need to match the expected output EXACTLY....I would suggest
		 * you look at the printf method (rather that the simple print of println).
		 * It operates the same in Java as it does in C and will make your code
		 * better.
		 * 
		 */
	}
	
	public static void printProfitByPizzaReport() throws SQLException, IOException 
	{
		/*
		 * Prints the ProfitByPizza view. Remember that this view
		 * needs to exist in your DB, so be sure you've run your createViews.sql
		 * files on your testing DB if you haven't already.
		 * 
		 * The result should be readable and sorted as indicated in the prompt.
		 * 
		 * HINT: You need to match the expected output EXACTLY....I would suggest
		 * you look at the printf method (rather that the simple print of println).
		 * It operates the same in Java as it does in C and will make your code
		 * better.
		 * 
		 */
	}
	
	public static void printProfitByOrderType() throws SQLException, IOException
	{
		/*
		 * Prints the ProfitByOrderType view. Remember that this view
		 * needs to exist in your DB, so be sure you've run your createViews.sql
		 * files on your testing DB if you haven't already.
		 * 
		 * The result should be readable and sorted as indicated in the prompt.
		 *
		 * HINT: You need to match the expected output EXACTLY....I would suggest
		 * you look at the printf method (rather that the simple print of println).
		 * It operates the same in Java as it does in C and will make your code
		 * better.
		 * 
		 */
	}
	
	
	
	/*
	 * These private methods help get the individual components of an SQL datetime object. 
	 * You're welcome to keep them or remove them....but they are usefull!
	 */
	private static int getYear(String date)// assumes date format 'YYYY-MM-DD HH:mm:ss'
	{
		return Integer.parseInt(date.substring(0,4));
	}
	private static int getMonth(String date)// assumes date format 'YYYY-MM-DD HH:mm:ss'
	{
		return Integer.parseInt(date.substring(5, 7));
	}
	private static int getDay(String date)// assumes date format 'YYYY-MM-DD HH:mm:ss'
	{
		return Integer.parseInt(date.substring(8, 10));
	}

	public static boolean checkDate(int year, int month, int day, String dateOfOrder)
	{
		if(getYear(dateOfOrder) > year)
			return true;
		else if(getYear(dateOfOrder) < year)
			return false;
		else
		{
			if(getMonth(dateOfOrder) > month)
				return true;
			else if(getMonth(dateOfOrder) < month)
				return false;
			else
			{
				if(getDay(dateOfOrder) >= day)
					return true;
				else
					return false;
			}
		}
	}


}