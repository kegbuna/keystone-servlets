/* KEYSTONE REQUEST CREATE SERVLET ---------------------------------------------------------
   Author: Ryan Ahern
   Date: June 2, 2012
   
-------------------------------------------------------------------------------------------- */

import java.io.IOException;
import javax.servlet.*;
import javax.servlet.http.*;
import java.util.*;
import com.bmc.arsys.api.*;
import org.json.JSONObject;

import java.io.PrintWriter;

public class RequestCreateServlet extends KeystoneServletBase {
	// Constants ----------------------------------------------------------------------------------
    private String FORM_NAME	    = "HPD:IncidentInterface_Create";
	
	/* Enable this variable to see error messages if Attachments/IDs/etc are not found */
	private boolean DEBUG_ENABLED   = true;

	public RequestCreateServlet() {
		super();
        this.SetServletName( "RequestSubmitServlet" );
	}
	
	protected void doPost( HttpServletRequest request, HttpServletResponse response ) throws ServletException, IOException {
		this.ClearParamMap();
		this.GetProperties();
        this.ParseParameters( request );

        FORM_NAME = this.GetParameter("Form");
        
        if ( !FORM_NAME.equals("NONE") ) {
            this.CreateRequest( response );
        }
        else {
            PrintWriter pw = response.getWriter();
            pw.println("Error: Servlet requires parameter \"Form\" to be provided.");
            pw.close();
        }
	} 
    
    private void CreateRequest( HttpServletResponse response ) {

        /* Log into AR Server */
        com.bmc.arsys.api.ARServerUser server = new com.bmc.arsys.api.ARServerUser();
        server.setServer( this.GetConfig("server") );
        server.setUser( this.GetConfig("username") );
        server.setPassword( this.GetConfig("password") );

        if ( !this.GetConfig("port").equals("") ) {
            int port = Integer.parseInt( this.GetConfig("port") );
            if ( port > 0 ) {
                server.setPort( port );
            }
        }
        
        try {
            
            /* Check the config map to make sure there was not an error */
            if ( !this.GetConfig("ERROR").equals("") ) {
                PrintWriter pwConfig = response.getWriter();
                pwConfig.println("Configuration Error: " + this.GetConfig("ERROR") );
                pwConfig.close();
            }
            
            try {
                server.verifyUser();
            }
            catch (ARException e) {
                PrintWriter pw = response.getWriter();
                pw.println( "{\"Error\":\"[VerifyUser] " + e.getMessage() + "\"}");
                pw.close();
                server.logout();
            }
            
            /* Build entry */
            Entry newEntry = new Entry();
            
            Iterator it = GetParameterIterator();
            while ( it.hasNext() ) {
                Map.Entry param = (Map.Entry)it.next();
                
                /* Ignore parameters that are not Remedy Fields */
                if ( !param.getKey().toString().equals("Form") ) {
                    newEntry.put( Integer.valueOf(param.getKey().toString()), new Value(param.getValue().toString()) );
                }
            }
            
            newEntry.put( 1000000076, new Value("CREATE") );
            
            JSONObject jsonResult = new JSONObject();
            try {
                String resultId = server.createEntry( FORM_NAME, newEntry );
                
                jsonResult.put( "Result", "SUCCESS" );
                jsonResult.put( "EntryId", resultId );
                jsonResult.put( "Id", GetRequestId(server, resultId) );
                
            }
            catch ( ARException e ) {
                jsonResult.put( "Result", e.getMessage() );
            }
            
            PrintWriter pw = response.getWriter();
            pw.println( jsonResult.toString() );
            pw.close();
            server.logout();
        }
        catch ( Exception e ) {
            if (DEBUG_ENABLED) {
                server.logout();
                
                try {
                    PrintWriter pw = response.getWriter();
                    pw.println(GetServletName() + " Error: " + e.getMessage() + "(Form: " + FORM_NAME + ")" );
                    pw.close();
                }
                catch ( Exception ioe ) { }
            }
        }
    }
    
    private String GetRequestId( ARServerUser server, String entryId ) throws ARException {
    	OutputInteger nMatches = new OutputInteger();
		List<SortInfo> sortOrder = new ArrayList<SortInfo>();
		sortOrder.add(new SortInfo(1, Constants.AR_SORT_ASCENDING));
		
        String qualStr = "('Request ID' = \"" + entryId + "\")";
        QualifierInfo qual = server.parseQualification( FORM_NAME, qualStr );
        int[]   fields    = {1,1000000161};
		List<Entry> entryList = server.getListEntryObjects(FORM_NAME, qual, 0, 1, sortOrder, fields, true, nMatches);
		
        
		if ( nMatches.intValue() > 0 ) {
			return entryList.get(0).get(1000000161).toString();
		}
		return "NONE";
    }
}
