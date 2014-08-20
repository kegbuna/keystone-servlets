/* KEYSTONE PAGE DATA SERVLET  -------------------------------------------------------------
   Author: Ryan Ahern
   Date: MaY 31, 2012
   
   Fetches data for each "screen" of the Keystone Portal.
   This servlet takes the following inputs:
   + Name - The name of the data to fetch.
   + Type - html, css, js
-------------------------------------------------------------------------------------------- */

import java.io.IOException;
import javax.servlet.*;
import javax.servlet.http.*;
import java.util.*;
import com.bmc.arsys.api.*;
import org.json.JSONObject;

import java.io.PrintWriter;

public class PageDataServlet extends KeystoneServletBase {
	// Constants ----------------------------------------------------------------------------------
    private String FORM_NAME	    = "COL:Keystone:PageData";
    private String PAGE_NAME        = "NONE";
    private String DATA_TYPE        = "HTML";
    private int    FIELD_ID_TYPE    = 802277002;
    private int    FIELD_ID_DATA    = 802277003;
	
	/* Enable this variable to see error messages if Attachments/IDs/etc are not found */
	private boolean DEBUG_ENABLED   = true;

	public PageDataServlet() {
		super();
	}
	
	protected void doPost( HttpServletRequest request, HttpServletResponse response ) throws ServletException, IOException {
		this.ClearParamMap();
		this.GetProperties();
        this.ParseParameters( request );

        PAGE_NAME = this.GetParameter("Name");
        DATA_TYPE = this.GetParameter("Type").toUpperCase();
        
        if ( !PAGE_NAME.equals("NONE") ) {
            this.GetPageData( response );
        }
        else {
            PrintError( response, "Error: Servlet requires parameter \"Name\" to be provided." );
        }
	} 
    
    private void GetPageData( HttpServletResponse response ) {

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
                PrintError( response, "Configuration Error: " + this.GetConfig("ERROR") );
            }
            
            OutputInteger nMatches = new OutputInteger();
			List<SortInfo> sortOrder = new ArrayList<SortInfo>();
			sortOrder.add(new SortInfo(1, Constants.AR_SORT_ASCENDING));
			
			//List<Field> form_fields = server.getListFieldObjects(form, Constants.AR_FIELD_TYPE_DATA);
            
            String qualStr = "('Page Name' = \"" + PAGE_NAME + "\")";
            QualifierInfo qual = server.parseQualification( FORM_NAME, qualStr );
            
			int[] field_ids = {1,7,802277001,802277002,802277003};
			
			List<Entry> entryList = server.getListEntryObjects(FORM_NAME, qual, 0, Constants.AR_NO_MAX_LIST_RETRIEVE, sortOrder, field_ids, true, nMatches);
			
            PrintWriter pw = response.getWriter();
			if ( nMatches.intValue() > 0 ) {
                
				JSONObject json = new JSONObject();
				
                Iterator iter = entryList.iterator();
                while ( iter.hasNext() ) {
                	Entry entry = (Entry)iter.next();
                	Integer type = entry.get(FIELD_ID_TYPE).getIntValue();
                	
	                /* Format the MIME Type response so that the browser renders the data */
	                if ( type == 0 ) {
	                   // response.setContentType( "text/html" );
	                    json.put( "HTML", entry.get(FIELD_ID_DATA) );
	                }
	                else if ( type == 1 ) {
	                	//response.setContentType( "text/javascript" );
	                	json.put( "JS", entry.get(FIELD_ID_DATA) );
	                }
	                else if ( type == 2 ) {
	                	//response.setContentType( "text/css" ); 
	                	json.put( "CSS", entry.get(FIELD_ID_DATA) );
	                }
	                else if ( type == 3 ) {
	                	//response.setContentType( "application/json" );
	                	json.put( "JSON", entry.get(FIELD_ID_DATA) );
	                }
                }
                
                response.setContentType( "application/json" );
                
                /* Output the page data */
				pw.println( json.toString() );
            }
            else {
                PrintError( response, "Error: No Page Data entries found." );
            }
            pw.close();
            server.logout();
        }
        catch ( Exception e ) {
            if (DEBUG_ENABLED) {
                server.logout();
                
                try {
                    PrintError( response, e.getMessage() + " - AR Server: " + this.GetConfig("server") );
                }
                catch ( Exception ioe ) { }
            }
        }
    }
    
}
