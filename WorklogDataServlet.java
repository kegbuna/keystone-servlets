/* KEYSTONE WORKLOG DATA SERVLET  -----------------------------------------------------------
   Author: Ryan Ahern
   Date: May 31, 2012
   
   Fetches worklog data for a specific Incident. 
   This servlet takes the following inputs:
   + id - Request ID to be fetched
-------------------------------------------------------------------------------------------- */

import java.io.IOException;
import javax.servlet.*;
import javax.servlet.http.*;
import java.util.*;
import com.bmc.arsys.api.*;
import org.json.JSONObject;
import org.json.JSONArray;

import java.io.PrintWriter;

public class WorklogDataServlet extends KeystoneServletBase {
	// Constants ----------------------------------------------------------------------------------
    private String  FORM_NAME	    	= "HPD:Search-Worklog";
    private String ATTACHMENT_SERVLET	= "/keystone-portlet/AttachmentServlet";
    
    /* Fields: Status, Summary, Notes, Submit Date, Liferay User, # Attachments */
    private int[] ATTACH_FIELDS  = {1000000351, 1000000352, 1000000353};
    private int[]   FIELD_IDS    = {1,7,1000003610,301394441,1000000157,1000000365,536870914,ATTACH_FIELDS[0],ATTACH_FIELDS[1],ATTACH_FIELDS[2]};
    
	
	/* Enable this variable to see error messages if Attachments/IDs/etc are not found */
	private boolean DEBUG_ENABLED   = true;

	public WorklogDataServlet() {
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
            this.GetWorklogData( response, requestId );
        }
        else {
            this.PrintError(response, "Error: Servlet requires parameter \"id\" to be provided."); 
        }
	} 
    
    private void GetWorklogData( HttpServletResponse response, String requestId ) {

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
            
            String qualStr = "('Incident Number' = \"" + requestId + "\") AND (\'View Access\' = \"Public\")";
            QualifierInfo qual = server.parseQualification( FORM_NAME, qualStr );
			
			List<Entry> entryList = server.getListEntryObjects(FORM_NAME, qual, 0, Constants.AR_NO_MAX_LIST_RETRIEVE, sortOrder, FIELD_IDS, true, nMatches);
			
            
			if ( nMatches.intValue() > 0 ) {
                
				JSONObject jsonMain = new JSONObject();
				JSONArray jsonArray = new JSONArray();
                
				int count = 0;
                Iterator iter = entryList.iterator();
                while ( iter.hasNext() ) {
                    Entry entry = (Entry)iter.next();
                    JSONObject jsonRow = new JSONObject();
					JSONObject json = new JSONObject();
				
					jsonRow.put( "id", count );
					
					String worklogId = entry.get(1).toString();
					worklogId = worklogId.substring(worklogId.indexOf("WLG"), worklogId.length());
					
					json.put( "Worklog ID", worklogId );
	                json.put( "Status", STATUS_MAP.get(entry.get(7).getValue()) );
	                json.put( "Summary", entry.get(1000003610) );
	                json.put( "Notes", entry.get(301394441) );
	                json.put( "Submit Date", this.ConvertTimestamp(entry.get(1000000157)) );
	                json.put( "Liferay User", entry.get(536870914) );
	                
	                /* Make sure attachment number isn't null (This is possible) */

						//int num = entry.get( 1000000365 ).getIntValue();
						
						/* Iterate through numbe;r of attachments and generate links for each one */
						String attachmentLinks = "";
						for ( int j=0; j <= 2; ++j ) {
							/* Fetch Attachment filenames for only the worklog number provided in the URL */
			                Value val = entry.get( ATTACH_FIELDS[j] );
							AttachmentValue aval = (AttachmentValue)val.getValue();
							
							/* Attachment will be null if there is nothing stored */
			                if ( aval != null ) {
								String name = aval.getName();
								int slash = name.lastIndexOf( 0x5C ) + 1; 
								
								/* Parse out the full path name containing C:\Path\To\File (If it exists) */
								if ( slash > -1 ) {
									name = name.substring( slash ); 
								}
							
								attachmentLinks += "<div><a href=\"" + ATTACHMENT_SERVLET + "/" + requestId + "/" + worklogId + "/" + (j+1) + "\" target=\"_blank\">" + name + "</a></div>";
			                }
						}
						json.put( "Attachments", attachmentLinks );
		                jsonRow.put( "cell", json );
		                jsonArray.put( jsonRow );
		                ++count;
	                
                }
                jsonMain.put( "total", nMatches );
                jsonMain.put( "page", 1 );
                jsonMain.put( "rows", jsonArray );
                
                response.setContentType( "application/json" );
                response.addHeader("Cache-Control", "no-cache,no-store,private,must-revalidate,max-stale=0,post-check=0,pre-check=0");
                
                /* Output the page data */
                PrintWriter pw = response.getWriter();
                
				pw.println( jsonMain.toString() );
				pw.close();
            }
            else {
                this.PrintError( response, "Error: No entries found." );
            }
            
            server.logout();
        }
        catch ( Exception e ) {
            if (DEBUG_ENABLED) {
                server.logout();
                
                try {
                    PrintWriter pw = response.getWriter();
                    pw.println("[Servlet] Error: " + e.getMessage() + " (" + this.GetConfig("server") + "," + this.GetConfig("username") + ")");
                    pw.close();
                }
                catch ( Exception ioe ) { }
            }
        }
    }
    
}
