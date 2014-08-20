/* KEYSTONE REQUEST DATA SERVLET  -----------------------------------------------------------
   Author: Ryan Ahern
   Date: May 31, 2012
   
   Fetches data for a single request.
   This servlet takes the following inputs:
   + id - Request ID to be fetched
-------------------------------------------------------------------------------------------- */

import java.io.IOException;
import javax.servlet.*;
import javax.servlet.http.*;
import java.util.*;
import com.bmc.arsys.api.*;
import org.json.JSONObject;

import java.io.PrintWriter;

public class RequestDataServlet extends KeystoneServletBase {
	// Constants ----------------------------------------------------------------------------------
    private String  FORM_NAME	    = "HPD:Help Desk Classic";
    
    /* Fields: Incident ID, Status, Summary, Notes, Op Cat 1,2,3, Prod Cat 1,2,3, Assigned Group,Urgency,Impact,Destination,LifeRayId,SAP ID,SAP Name,Related Ticket,Liferay Email, Liferay Fullname, Liferay Phone,Dialed # 1-5, LifeRay User ID, Liferay Full Name, LifeRay Email, Liferay Phone */
    private int[]   FIELD_IDS    = {1,1000000161,7,1000000000,1000000151,1000000063,1000000064,1000000065,200000003,200000004,200000005,1000000217,1000000162,1000000163,750000025,750000590,750000582,750000589,750009063,750000595,750000594,750000596,750000008,750000009,750000010,750009003,750009004,750000590,750000590,750000594,750000595,750000596};
	
	/* Enable this variable to see error messages if Attachments/IDs/etc are not found */
	private boolean DEBUG_ENABLED   = true;

	public RequestDataServlet() {
		super();
		
		/* Initialize conversion maps for use throughout this class */
		BuildMaps();
	}
	
	protected void doPost( HttpServletRequest request, HttpServletResponse response ) throws ServletException, IOException {
		this.ClearParamMap();
		this.GetProperties();
        this.ParseParameters( request );

        String requestId = GetParameter("id");
        if ( !requestId.equals("NONE") ) {
            this.GetRequestData( response, requestId );
        }
        else {
            this.PrintError(response, "Error: Servlet requires parameter \"id\" to be provided."); 
        }
	} 
    
    private void GetRequestData( HttpServletResponse response, String requestId ) {

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
                this.PrintError( response, "Configuration Error: " + this.GetConfig("ERROR") );
            }
            
            OutputInteger nMatches = new OutputInteger();
			List<SortInfo> sortOrder = new ArrayList<SortInfo>();
			sortOrder.add(new SortInfo(1, Constants.AR_SORT_ASCENDING));
			
			//List<Field> form_fields = server.getListFieldObjects(form, Constants.AR_FIELD_TYPE_DATA);
            
            String qualStr = "('Incident Number' = \"" + requestId + "\")";
            QualifierInfo qual = server.parseQualification( FORM_NAME, qualStr );
			
			List<Entry> entryList = server.getListEntryObjects(FORM_NAME, qual, 0, 1, sortOrder, FIELD_IDS, true, nMatches);
			
            
			if ( nMatches.intValue() > 0 ) {
                
				JSONObject json = new JSONObject();
			
				json.put( "Entry ID", entryList.get(0).get(1) ); 
                json.put( "Status", STATUS_MAP.get(entryList.get(0).get(7).getValue()) );
                json.put( "Summary", entryList.get(0).get(1000000000) ); 
                json.put( "Notes", entryList.get(0).get(1000000151) );
                json.put( "Op Categorization 1", entryList.get(0).get(1000000063) );
                json.put( "Op Categorization 2", entryList.get(0).get(1000000064) );
                json.put( "Op Categorization 3", entryList.get(0).get(1000000065) );
                json.put( "Op Categorization 3", entryList.get(0).get(1000000065) );
                json.put( "Urgency", URGENCY_MAP.get(entryList.get(0).get(1000000162).getValue()) ); 
                json.put( "Impact", IMPACT_MAP.get(entryList.get(0).get(1000000163).getValue()) );
                json.put( "Destination", entryList.get(0).get(750000025) );
                json.put( "Liferay ID", entryList.get(0).get(750000590) );
                json.put( "Liferay Name", entryList.get(0).get(750000594) );
                json.put( "Liferay Email", entryList.get(0).get(750000595) );
                json.put( "Liferay Phone", entryList.get(0).get(750000596) );
                json.put( "SAP ID", entryList.get(0).get(750000582) );
                json.put( "SAP Name", entryList.get(0).get(750000589) ); 
                json.put( "Related Ticket ID", entryList.get(0).get(750009063) );
                json.put( "Dialed Number 1", entryList.get(0).get(750000008) ); 
                json.put( "Dialed Number 2", entryList.get(0).get(750000009) ); 
                json.put( "Dialed Number 3", entryList.get(0).get(750000010) ); 
                json.put( "Dialed Number 4", entryList.get(0).get(750009003) ); 
                json.put( "Dialed Number 5", entryList.get(0).get(750009004) ); 
                
                response.setContentType( "application/json" );
                
                /* Output the page data */
                PrintWriter pw = response.getWriter();
				pw.println( json.toString() );
				pw.close();
            }
            else {
                this.PrintError( response, "Error: No Page Data entries found." );
            }
            
            server.logout();
        }
        catch ( Exception e ) {
            if (DEBUG_ENABLED) {
                server.logout();
                
                try {
                    PrintWriter pw = response.getWriter();
                    pw.println("[Servlet] Error: " + e.getMessage() + this.GetConfig("server") + "," + this.GetConfig("username") +","+this.GetConfig("password"));
                    pw.close();
                }
                catch ( Exception ioe ) { }
            }
        }
    }
    
}
