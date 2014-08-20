/* SEARCH REQUEST SERVLET ----------------------------------------------------------------------
   Author: Ryan Ahern
   Date: April 2, 2012
   
   Input Parameter:
   - Qualification[]
       - Pairs of parameters with "Qualification" as the key
   - SearchIncident
   - Date1
   - Date2
   - FilterType
       - Submitted By Me
       - Submitted By My Company
-------------------------------------------------------------------------------------------- */
import java.io.*;
import java.io.IOException;
import javax.servlet.*;
import javax.servlet.http.*;
import java.util.*;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import com.bmc.arsys.api.*;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;


public class RequestListServlet extends HttpServlet {
	// Constants ----------------------------------------------------------------------------------
    private String SERVLET_NAME     = "[RequestListServlet]";
    private String FORM_INCIDENT	= "HPD:Help Desk Classic";
    private String FORM_WORK_INFO	= "HPD:Search-Worklog";
    private String ARS_HOSTNAME;
    private String API_AUTH_USER;
    private String API_AUTH_PASS;
    private String API_AUTH_COMPANY; /* Temp to be used for a qual */
    private int ARS_PORT            = 0;
    private DateFormat DATE_FORMAT  = new SimpleDateFormat("MM/dd/yyyy hh:mm a");
	
	/* Maps Incoming Qualification Type to Exact ARS Qual String */
    private Map<String,String> QUAL_MAP = new HashMap<String,String>();
    private Map<Integer,String> STATUS_MAP = new HashMap<Integer,String>();
    
	public RequestListServlet() {
		super();
	}

	/**
	 * Builds a Map containing all possible qualifications. This hard-configured Map prevents custom
	 * qualifications from being injected into the servlet by curious or malicious users.
	 * NOTE: Make sure to call this after the configuration parameters have been read in.
	 */
	public void BuildQualificationMap() { 
        QUAL_MAP.put( "Submitted By Me", "( \'Submitter\' = \"" + API_AUTH_USER + "\" )");
	    QUAL_MAP.put( "Submitted By My Company", "( \'Company\' = \"" + API_AUTH_COMPANY + "\" )");
	    QUAL_MAP.put( "Status_Open", "( \'Status\' < \"Resolved\" )");
	    QUAL_MAP.put( "Status_Closed", "( \'Status\' > \"Pending\" )");
	    
	    /* Need a better way to configure this */
	    STATUS_MAP.put(0, "New");
	    STATUS_MAP.put(1, "Assigned");
	    STATUS_MAP.put(2, "In Progress");
	    STATUS_MAP.put(3, "Pending");
	    STATUS_MAP.put(4, "Resolved");
	    STATUS_MAP.put(5, "Closed");
	    STATUS_MAP.put(6, "Cancelled");
    }
	
	private String ConvertTimestamp( Value val ) {
        Timestamp ts = (Timestamp)val.getValue();
        Date sub_date_s = ts.toDate();
        return DATE_FORMAT.format(sub_date_s);
    }
	
	/* Forward any GET requests to doPost */
	protected void doGet( HttpServletRequest request, HttpServletResponse response ) throws ServletException, IOException {		
	    this.doPost( request, response );
	} 
	
	protected void doPost( HttpServletRequest request, HttpServletResponse response ) throws ServletException, IOException {
	    Map<String,String> config = GetProperties();
        ARS_HOSTNAME    = config.get("server");
        API_AUTH_USER   = config.get("username");
        API_AUTH_PASS   = config.get("password");
        API_AUTH_COMPANY= config.get("company");
        
        if ( config.get("port") != null ) {
            ARS_PORT    = Integer.parseInt(config.get("port"));
        } else {
            ARS_PORT = 0;
        }
        
        /* Now that configuration has been read, build the Qualification Map */
        BuildQualificationMap();
        
        /* Fetch Request Parameters */
        String[] qualArray 		= request.getParameterValues("Qualification[]");
        String requestId 		= request.getParameter("SearchIncident");
        String relatedRequestId = request.getParameter("SearchRelatedIncident");
        String filterType 		= request.getParameter("FilterType");
        String destination 		= request.getParameter("Destination");
        String dateRange1 		= request.getParameter("Date1"); //'Submit Date' > "05/01/12"
        String dateRange2 		= request.getParameter("Date2");
        String companyId 		= request.getParameter("CompanyId");
        String loginId 			= request.getParameter("LoginId");
        String csvOutput 		= request.getParameter("csv");
        
        /* Form submits handle arrays differently, so if CSV is desired, grab the form array qual (Since CSV is a form submit) */
        if ( csvOutput != null ) {
        	qualArray 			= request.getParameterValues("Qualification");
        }
        
        /* Build Qualification String from Parameters */ 
        //String qualStr = GetQualificationString( qualArray );
        String qualStr = "";
        
        if ( filterType != null ) {
	        if ( filterType.equals("Submitted By Me") ) {
	        	qualStr = "( \'Liferay Login ID\' = \"" + loginId + "\" ) AND ( \'SAP_ID\' = \"" + companyId + "\" )";
	        }
	        else if ( filterType.equals("Submitted By My Company") ) {
        		if ( companyId != null ) {
        			qualStr = "( \'SAP_ID\' = \"" + companyId + "\" )";
        		}
	        
	        }
        }
        
        /* If an incident is to be searched, provide the INC ID */
        if ( requestId != null ) {
	        if ( !requestId.equals("") ) {
	            qualStr += " AND (\'Incident Number\' LIKE \"%" + requestId + "%\")";
	        }
        }
        if ( relatedRequestId != null ) {
	        if ( !relatedRequestId.equals("") ) {
	            qualStr += " AND (\'Related Ticket ID\' LIKE \"%" + relatedRequestId + "%\")";
	        }
        }
        /* If a date range is provided, add it to the qualification */
        
        if ( (dateRange1 != null) ) {
        	if ( !dateRange1.equals("") ) {
        		qualStr += " AND ( \'Submit Date\' >= \"" + dateRange1 + " 00:00:00\" )";
        	}
        }
        if ( (dateRange2 != null) ) {
        	if ( !dateRange2.equals("") ) {
        		qualStr += " AND ( \'Submit Date\' <= \"" + dateRange2 + " 23:59:59\" )";
        	}
        }
        
        if ( destination != null ) {
	        if ( !destination.equals("") ) {
	        	qualStr += " AND (\'Country Destination\' = \"" + destination + "\" )";
	        }
        }
        
        if ( qualArray != null ) {
	        if ( Arrays.asList(qualArray).contains("Status_Open") && !Arrays.asList(qualArray).contains("Status_Closed") ) {
	        	qualStr += " AND ( \'Status\' < \"Resolved\" )";
	        }
	        else if ( Arrays.asList(qualArray).contains("Status_Open") && Arrays.asList(qualArray).contains("Status_Closed") ) {
	        	qualStr += " AND (( \'Status\' < \"Resolved\" ) OR ( \'Status\' > \"Pending\" ))";
	        }
	        else if ( !Arrays.asList(qualArray).contains("Status_Open") && Arrays.asList(qualArray).contains("Status_Closed") ) {
	        	qualStr += " AND ( \'Status\' > \"Pending\" )";
	        }
        }
        
        /* If no qualification is provided, print an error */
        if ( qualStr != null ) {
        	boolean csv = false;
        	if ( csvOutput != null ) {
        		csv = true;
        	}
        	FetchIncidentList( response, qualStr, csv );
    	}
        else {
        	PrintError( response, " Error with qualification: " + qualStr );
        }
	}
	
	private void FetchIncidentList( HttpServletResponse response, String qualStr, boolean csv ) throws IOException {
        com.bmc.arsys.api.ARServerUser server = new com.bmc.arsys.api.ARServerUser();
        server.setServer( ARS_HOSTNAME );
        server.setUser( API_AUTH_USER );
        server.setPassword( API_AUTH_PASS );

        if ( ARS_PORT > 0 ) {
            server.setPort( ARS_PORT );
        }
        
        try {
            server.verifyUser();
        }
        catch (ARException e) {
            PrintWriter pw = response.getWriter();
            pw.println( "{\"Error\":\"[VerifyUser] " + e.getMessage() + "\"}" );
            pw.close();
            server.logout();
        }
        
        try {
            
            List <Field> fields = server.getListFieldObjects( FORM_INCIDENT );

            // Create the search qualifier.
            QualifierInfo qual = server.parseQualification(qualStr, fields, null, Constants.AR_QUALCONTEXT_DEFAULT);
            
            /* Fields: Incident ID, Status, Summary, Notes, Prod Cat 1,2,3, Op Cat 1,2,3, Assigned Group, Contact Company,Submit Date, Last Modified Date,Country,Vendor ID, Related Ticket ID, Destination, LifeRay User ID, Liferay Full Name, LifeRay Email, Liferay Phone, Product Name  */
            int[] fieldIds = {1000000161,7,1000000000,1000000151,200000003,200000004,200000005,1000000063,1000000064,1000000065,1000000217,1000000082,3,6,1000000002,1000000652,750009063,750000025,750000590,750000594,750000595,750000596, 240001002 };
            
            /* Integer to pass to getList function - Will be populated with the number of results found. */
            OutputInteger nMatches = new OutputInteger();
            
            List<SortInfo> sortOrder = new ArrayList<SortInfo>();
            sortOrder.add(new SortInfo(1, Constants.AR_SORT_ASCENDING));
            
            /* API Call: Grab a list of Incident */
            List<Entry> entryList = server.getListEntryObjects( FORM_INCIDENT, qual, 0, Constants.AR_NO_MAX_LIST_RETRIEVE, sortOrder, fieldIds, true, nMatches);

            if ( nMatches.intValue() > 0 ) {
                
            	JSONObject jsonMain = new JSONObject();
				JSONArray jsonArray = new JSONArray();
				String csvData = "Incident ID, Your Company Ticket, Issue Type, Status, Destination, Submit Date, Modified Date, Summary, Notes\n";
                
				int count = 0;
                Iterator iter = entryList.iterator();
                while ( iter.hasNext() ) {
                    Entry entry = (Entry)iter.next();
                    
                    String incId    = entry.get(1000000161).toString();
                    String status   = STATUS_MAP.get( entry.get(7).getValue() );
                    String summary  = entry.get(1000000000).toString();
                    String notes    = entry.get(1000000151).toString(); 
                    String group    = entry.get(1000000217).toString();
                    String prodcat1 = entry.get(200000003).toString();
                    String prodcat2 = entry.get(200000004).toString(); 
                    String prodcat3 = entry.get(200000005).toString();
                    String opcat3 = entry.get(1000000065).toString();
                    String country  = entry.get(1000000002).toString();
                    String destination  = entry.get(750000025).toString();
                    String vendorTicketId = entry.get(1000000652).toString();
                    String date_submit = ConvertTimestamp( entry.get(3) ); 
                    String date_mod = ConvertTimestamp( entry.get(6) ); 
                    String rel_ticket_id = entry.get(750009063).toString();
                    String product_name = entry.get(240001002).toString();
                    
                    /* Clear out nulls */
                    if ( rel_ticket_id == null ) {
                    	rel_ticket_id = "";
                    }
                    
                    if ( destination == null ) {
                    	destination = "";
                    }

                    /* Create a single JSON object to insert into the JSON Array */
                    JSONObject jsonRow = new JSONObject();
					JSONObject json = new JSONObject();
						
					/* Output as CSV if CSV var is true */
                    if ( !csv ) {
						try {
							jsonRow.put( "id", count );
						}
						catch ( JSONException e ) {
	                        PrintWriter pw = response.getWriter();pw.println(SERVLET_NAME + " JSON Error: " + e.getMessage());pw.close();
	                        server.logout();
	                    }
						
	                    try {
	                        json.put( "Request ID", incId );
	                        json.put( "Summary", summary );
	                        json.put( "Notes", notes );
	                        json.put( "Status", status );
	                        json.put( "Assigned Group", group );
	                        json.put( "Last Modified Date", date_mod );
	                        json.put( "Submit Date", date_submit );
	                        json.put( "ProdCat3", prodcat3 );
	                        json.put( "OpCat3", opcat3 );
	                        json.put( "Country", country );
	                        json.put( "Destination", destination );
	                        json.put( "Vendor Ticket ID", vendorTicketId );
	                        json.put( "Related Ticket ID", rel_ticket_id );
	                        json.put( "Product Name", product_name );
	                        /* Add JSON object to Array */
	                        jsonRow.put( "cell", json );
	                        jsonArray.put( jsonRow );
	                    }
	                    catch ( JSONException e ) {
	                        PrintWriter pw = response.getWriter();pw.println(SERVLET_NAME + " JSON Error: " + e.getMessage());pw.close();
	                        server.logout();
	                    }
                    }
                    else {
                    	csvData += "\"" + incId + "\",\"" + rel_ticket_id + "\",\"" + opcat3 + "\",\"" + status + "\",\""  + destination + "\",\"" + date_submit + "\",\"" + date_mod + "\",\""  + summary + "\",\"" + notes + "\"\n";
                    }
                }
                
                if ( !csv ) {
	                try {
		                jsonMain.put( "total", nMatches );
		                jsonMain.put( "page", 1 );
		                jsonMain.put( "rows", jsonArray );
	                }
	                catch ( JSONException e ) {
	                    PrintWriter pw = response.getWriter();pw.println(SERVLET_NAME + " JSON Error: " + e.getMessage());pw.close();
	                    server.logout();
	                }
                
	                response.setContentType( "text/javascript" );
	                response.addHeader("Cache-Control", "no-cache,no-store,private,must-revalidate,max-stale=0,post-check=0,pre-check=0");
	                
	                /* Ouput the resulting JSON Array */
	                PrintWriter pwOutput = response.getWriter();
	                pwOutput.println( jsonMain.toString() );
	                pwOutput.close();
                }
                else {
                	response.setContentType("text/comma-separated-values;charset=UTF-8");
	                response.addHeader("Cache-Control", "private,max-age=15"); /* REQUIRED FOR IE 7/8 WHEN USING SSL */
	                response.setHeader( "Content-Disposition", "attachment; filename=\"tickets.csv\"" );
	                
	                /* Ouput the resulting JSON Array */
	                PrintWriter csvOutput = response.getWriter();
	                csvOutput.println( csvData );
	                csvOutput.close();
                }
            }
            else {
            	PrintError( response, "Error: No requests found using qualification: " + qualStr );
                server.logout();
            }
        }
        catch (ARException e) {
            PrintError( response, e.getMessage() );
            server.logout();
        }
        
        server.logout();
    }
	
	/**
	 * The qualification parameters are passed into this servlet as keywords. The QUAL_MAP contains the 
	 * translation from a keyword to an exact Remedy-formatted Qualficiation String. This function 
	 * iterates through each keyword in the qualification array and builds a full Remedy qualification.
	 * @param String array
	 * @returns API-Compliant Qualification String 
	 */
	protected String GetQualificationString( String[] qualArray ) {
	    String qual = QUAL_MAP.get(qualArray[0]);
	    for (int i=1; i < qualArray.length; ++i) {
	        if ( !qualArray[i].equals("") ) {
	            qual += " AND " + QUAL_MAP.get(qualArray[i]);
	        }
	    }
	    return qual;
	}
	
	/** 
     *  Parse the Configuration YML file
     */
    private Map<String,String> GetProperties() {
        Map<String,String> propertyMap = new HashMap<String,String>();
        try{
            FileInputStream yaml = new FileInputStream(getServletContext().getRealPath("/config/context.yml"));
            DataInputStream in = new DataInputStream(yaml);
            BufferedReader config = new BufferedReader(new InputStreamReader(in));
        
            String line;
            while((line = config.readLine()) != null){
                if(line.contains("\t")){
                    line = line.trim();
                    String[] pair = line.split(":");
                    if(pair.length > 1){
                        propertyMap.put(pair[0].trim(), pair[1].trim());
                    } else {
                        propertyMap.put(pair[0].trim(), null);
                    }
                }
            }
            in.close();
        }
        catch(Exception e){
            e.printStackTrace();
        }
        return propertyMap;
    }
    
    private void PrintError( HttpServletResponse response, String error ) {
    	 JSONObject json = new JSONObject();
         
         try {
             json.put( "Error", error );
             PrintWriter pwOutput = response.getWriter();
             pwOutput.println( json.toString() );
             pwOutput.close();
         }
         catch ( Exception e ) {
             e.printStackTrace();
         }
    }
}
