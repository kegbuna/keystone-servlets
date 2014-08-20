/* REMEDY ARS ATTACHMENT SERVLET -------------------------------------------------------------------
   Author: Ryan Ahern
   Date: March 5, 2012
   
   Remedy Version: 7.6
   
   This servlet fetches attachments using the Remedy API. It specifically grabs
   attachments from the Work Details section of the Incident ticket.
   
   URL FORMAT
   1. /AttachmentServlet/INC000000006826/
      Calls FetchWorkDetailsList() and outputs a list 
-------------------------------------------------------------------------------------------- */
import java.io.*;
import java.io.IOException;
import javax.servlet.*;
import javax.servlet.http.*;
import java.util.*;
import com.bmc.arsys.api.*;

import java.io.PrintWriter;

public class AttachmentServlet extends HttpServlet {
	// Constants ----------------------------------------------------------------------------------
	private String FORM_WORK_INFO_CRQ		= "CHG:Chg Search-Worklog";
	private String FORM_WORK_INFO_INC		= "HPD:Search-Worklog";
	private String FORM_WORK_INFO			= FORM_WORK_INFO_INC;
	private String QUAL_REQUEST_FIELD_CRQ	= "Infrastructure Change ID";
	private String QUAL_REQUEST_FIELD_INC	= "Incident Number";
	private String QUAL_REQUEST_FIELD		= QUAL_REQUEST_FIELD_INC;
	private String ARS_HOSTNAME 			= "dkremedys01";
	private String API_AUTH_USER 			= "KD_WEBUSER";
	private String API_AUTH_PASS 			= "KD_WEBUSER";
	private int	   ARS_PORT	        		= 4001;
	private String WORKLOG_SUMMARY_SEARCH 	= "Change Portfolio Attachments"; // Summary for "Attachment Only" worklogs
	private String BACKGROUND_COLOR 		= "#DEDEDE";
	private String CONFIG_FILE_PATH			= "/config/context.yml";
	
	/* Enable this variable to see error messages if Attachments/IDs/etc are not found */
	private boolean DEBUG_ENABLED   = true;
	
	private int[] ATTACH_FIELDS     = {1000000351, 1000000352, 1000000353};

	public AttachmentServlet() {
		super();
	}
	
	protected void doGet( HttpServletRequest request, HttpServletResponse response ) throws ServletException, IOException {		
		Map<String,String> config = GetProperties();
        ARS_HOSTNAME    = config.get("server");
        API_AUTH_USER   = config.get("username");
        API_AUTH_PASS   = config.get("password");
        
        if ( config.get("port") != null ) {
            ARS_PORT    = Integer.parseInt(config.get("port"));
        } else {
            ARS_PORT = 0;
        }
        
		/* Grab request ID from the URL by parsing out slashes. Format: /INC0000XYZ/1/3 */
        String[] requestArray = request.getPathInfo().split("/");
		
        String requestId = "";
		String worklogId = "";
        int attachNum 	 = 0;
		
        /* Get Request Details: After parse, max of 3 indices in requestArray - 0-Blank, 1-ID, 2-Work Detail Number, 3-Attachment Number */
        if ( requestArray.length > 1 ) {
            requestId = requestArray[1];
        }
        if ( requestArray.length > 2 ) {
			worklogId = requestArray[2];
			
			/* Determine if there is an attachment number */
			if ( requestArray.length > 3 ) {
				attachNum = Integer.parseInt(requestArray[3]);
			}
        }
        
		if ( requestId.length() < 1 ) {
			PrintError( response, "Request ID is required." );
            return;
        }	
		
		/* Determine if we are looking for Incident Worklogs or Change Worklogs */
		if ( requestId.contains("CRQ") ) {
			FORM_WORK_INFO = FORM_WORK_INFO_CRQ;
			QUAL_REQUEST_FIELD = QUAL_REQUEST_FIELD_CRQ;
		}
		else {
			FORM_WORK_INFO = FORM_WORK_INFO_INC;
			QUAL_REQUEST_FIELD = QUAL_REQUEST_FIELD_INC;
		}
		
		/* Log into AR Server */
		com.bmc.arsys.api.ARServerUser server = new com.bmc.arsys.api.ARServerUser();
        server.setServer( ARS_HOSTNAME );
        server.setUser( API_AUTH_USER );
        server.setPassword( API_AUTH_PASS );
		
		if ( ARS_PORT > 0 ) {
			server.setPort( ARS_PORT );
		}
	
		/* Possible Outcomes:
			- Request ID is provided with no other info: Fetch Worklog List 
			- Request ID and worklog number is provided: Fetch Attachment List 
			- Request ID, worklog number, and attachment number are provided: Fetch Attachment Data 
		*/
		if ( (worklogId.length() < 1) ) {
		    FetchWorkDetailsList( request, response, requestId );
		}
		else if ( (worklogId.length() > 1) && (attachNum == 0) ) {
		    FetchWorkDetailsAttachmentList( request, response, requestId, worklogId );
		}
		else if ( (worklogId.length() > 1) && (attachNum > 0) && (attachNum <= 3) ) {
			FetchWorkDetailsAttachment( response, server, requestId, worklogId, attachNum );
		}
		else {
			PrintError( response, "Not enough parameters provided" );
		}
	} 
	
	protected void doPost( HttpServletRequest request, HttpServletResponse response ) throws ServletException, IOException {
		doGet( request, response );
	}
	
	private void FetchWorkDetailsAttachment ( HttpServletResponse response, com.bmc.arsys.api.ARServerUser server, String id, String worklogId, int number ) throws IOException {
        
        String qualStr = "( \'Work Log ID\' = \"" + worklogId + "\" )";
        int num = number - 1;
        
        try {
            server.verifyUser();
        }
        catch (ARException e) {
            PrintWriter pw = response.getWriter();
            pw.println( "[Verify User Error] " + e.getMessage() );
            pw.close();

        }
        try {
            
            List <Field> fields = server.getListFieldObjects( FORM_WORK_INFO );

            // Create the search qualifier.
            QualifierInfo qual = server.parseQualification(qualStr, fields, null, Constants.AR_QUALCONTEXT_DEFAULT);
            
            /* Fields: WorkLog ID, Req ID, Attachment 1,2,3 */
            int[] fieldIds = {1,1000000182,7,ATTACH_FIELDS[num]};
            
            /* Integer to pass to getList function - Will be populated with the number of results found. */
            OutputInteger nMatches = new OutputInteger();
            
            List<SortInfo> sortOrder = new ArrayList<SortInfo>();
            sortOrder.add(new SortInfo(1, Constants.AR_SORT_ASCENDING));
            
            /* Only want 1 entry */
            List<Entry> entryList = server.getListEntryObjects( FORM_WORK_INFO, qual, 0, 1, sortOrder, fieldIds, true, nMatches);

            if ( nMatches.intValue() > 0 ) {
                
                
                /* Get the attachment Filename to determine filetype */
                Value val = entryList.get(0).get(ATTACH_FIELDS[num]);
                AttachmentValue aval = (AttachmentValue)val.getValue();
                
                if ( aval != null ) {
                    String attachName = aval.getName();
                    
                    /* Get attachment size and build a byte array from the attachment data */
                    int attachSize = (int)aval.getOriginalSize();
					
					if ( attachSize > 0 ) {
						byte [] rb = new byte[attachSize];
						try {
							rb = server.getEntryBlob(FORM_WORK_INFO, entryList.get(0).getEntryId(), ATTACH_FIELDS[num]);
						}
						catch ( IndexOutOfBoundsException ioe ) { /* Return a 404 if the data returned has length 0 */
							PrintWriter pw = response.getWriter();pw.println( "Error: " + ioe.getMessage() );pw.close();
							return;
						}
						
						// Init servlet response.
						response.reset();
						response.setBufferSize( rb.length );
						response.setContentType( getServletContext().getMimeType(attachName) );
						response.setHeader( "Content-Length", Integer.toString(rb.length) );
						response.setHeader( "Content-Disposition", "inline; filename=\"" + attachName );
						
						response.getOutputStream().write( rb, 0, rb.length );
						response.getOutputStream().flush(); 
					}
					else {
						if (DEBUG_ENABLED) {
							PrintWriter pw = response.getWriter();
							pw.println( "[AttachmentServlet] Error: Attachment size is 0 bytes." );
							pw.close();
						}
					}
                }
                else {
                    PrintWriter pw = response.getWriter();
                    pw.println( "[AttachmentServlet] Error: Attachment number " + number + " does not exist." );
                    pw.close();
                }
            }
            else {
                PrintWriter pw = response.getWriter();pw.println( "[AttachmentServlet] Error: No attachment found for Request ID " + id + ", Worklog ID: " + worklogId + " :: " + qualStr);pw.close();
            } 
        }
        catch (ARException e) {
			server.logout();
            PrintWriter pw = response.getWriter();
            pw.println("[AttachmentServlet] " + e.getMessage());
            pw.close();
        }
        
        server.logout();
    }
	
	/* This function takes a single Worklog ID and generates a list of the Attachments specific to only that worklog.
	 *   @param HTTP Request Object - Used to determine the current hostname, URL, and web server port
	 *   @param HTTP Response Object - The attachment link list is printed to the response
	 *   @param Request ID - Either for INC or CRQ
	 *   @param Worklog ID - In the format WLG00000000WXYZ
	 */
	private void FetchWorkDetailsAttachmentList ( HttpServletRequest request, HttpServletResponse response, String requestId, String worklogId ) throws IOException {
		com.bmc.arsys.api.ARServerUser server = Login( response );
		String qualStr = "( \'" + QUAL_REQUEST_FIELD + "\' = \"" + requestId + "\" ) AND ( \'Work Log ID\' = \"" + worklogId + "\" )";
		
		try {
			
			List <Field> fields = server.getListFieldObjects( FORM_WORK_INFO );

			// Create the search qualifier.
			QualifierInfo qual = server.parseQualification(qualStr, fields, null, Constants.AR_QUALCONTEXT_DEFAULT);
			
			/* Fields: Worklog ID, Status, Attachment 1, 2, 3 */
			int[] fieldIds = {1,7,ATTACH_FIELDS[0],ATTACH_FIELDS[1],ATTACH_FIELDS[2]};
			
			/* Integer to pass to getList function - Will be populated with the number of results found. */
			OutputInteger nMatches = new OutputInteger();
			
			List<SortInfo> sortOrder = new ArrayList<SortInfo>();
			sortOrder.add(new SortInfo(1, Constants.AR_SORT_ASCENDING));
			
			/* Only want 1 entry */
			List<Entry> entryList = server.getListEntryObjects( FORM_WORK_INFO, qual, 0, 1, sortOrder, fieldIds, true, nMatches);

			if ( nMatches.intValue() > 0 ) {
				
			    String returnString = "Attachments for Worklog:<br>";
				for ( int i=0; i < 3; i++ ) {
				    /* Fetch Attachment filenames for only the worklog number provided in the URL */
	                Value val = entryList.get(0).get( ATTACH_FIELDS[i] );
	                AttachmentValue aval = (AttachmentValue)val.getValue();
	                
	                /* Attachment will be null if there is nothing stored */
	                if ( aval != null ) {
						String name = aval.getName();
						int slash = name.lastIndexOf( 0x5C ) + 1;
						
						/* Parse out the full path name containing C:\Path\To\File (If it exists) */
						if ( slash > -1 ) {
							name = name.substring( slash ); 
						}
						/* Generate a link to the Attachment itself */
						returnString += "<a href=\"" + request.getRequestURL() + "/" + "/" + (i+1) + "\" target=\"_blank\">" + name + "</a><br/>";
						
	                }
				}
				
				/* Return list HTML */
	            response.reset();
                response.setContentType( "text/html" );
                
                PrintWriter output = response.getWriter();
                output.println( returnString );
                output.close();
				
			}
			else {
				PrintWriter pw = response.getWriter();pw.println( "[AttachmentServlet] Error: No attachments found for ID: " + requestId );pw.close();
			}
		}
		catch (ARException e) {
			server.logout();
			PrintWriter pw = response.getWriter();
			pw.println("[AttachmentServlet] AR Error while fetching attachment list: " + e.getMessage());
			pw.close();
		}
		
		server.logout();
	}
	
	/* This function takes an existing INC/CRQ ID and fetches a list of worklogs.
	 *   @param HTTP Request Object - Used to determine the current hostname, URL, and web server port
	 *   @param HTTP Response Object - The attachment link list is printed to the response
	 *   @param Request ID - Either for INC or CRQ
	 */
	private void FetchWorkDetailsList ( HttpServletRequest request, HttpServletResponse response, String requestId ) throws IOException {
		com.bmc.arsys.api.ARServerUser server = Login( response );
		String qualStr = "( \'" + QUAL_REQUEST_FIELD + "\' = \"" + requestId + "\" )";
		
		try {
			
			List <Field> fields = server.getListFieldObjects( FORM_WORK_INFO );

			// Create the search qualifier.
			QualifierInfo qual = server.parseQualification(qualStr, fields, null, Constants.AR_QUALCONTEXT_DEFAULT);
			
			/* Fields: Worklog ID, Status, Summary, Notes, # Attachments */
			int[] fieldIds = {1,7,1000003610,301394441,1000000365,ATTACH_FIELDS[0],ATTACH_FIELDS[1],ATTACH_FIELDS[2]};
			
			/* Integer to pass to getList function - Will be populated with the number of results found. */
			OutputInteger nMatches = new OutputInteger();
			
			List<SortInfo> sortOrder = new ArrayList<SortInfo>();
			sortOrder.add(new SortInfo(1, Constants.AR_SORT_ASCENDING));
			
			/* Grab all work details for the given INC ID */
			List<Entry> entryList = server.getListEntryObjects( FORM_WORK_INFO, qual, 0, Constants.AR_NO_MAX_LIST_RETRIEVE, sortOrder, fieldIds, true, nMatches);

			if ( nMatches.intValue() > 0 ) {
				
			    String returnString = "";
				int totalAttachCount = 1;
				
				/* Iterate through each returned Worklog Entry and grab the Worklog info for each */ 
				for ( int i=0; i < nMatches.intValue(); ++i ) {
					String worklogJoinId = entryList.get(i).get( 1 ).toString();
					String worklogId = worklogJoinId.substring( worklogJoinId.indexOf("|")+1 );
					
					returnString += "<div>" + worklogId + ": " + entryList.get(i).get( 1000003610 ).toString() + "</div>";
					returnString += "<div>" + entryList.get(i).get( 301394441 ).toString() + "</div>";
					
					/* Make sure attachment number isn't null (This is possible) */
					//int num = entryList.get(i).get( 1000000365 ).getIntValue();
						
					/* Iterate through number of attachments and generate links for each one */
					for ( int j=0; j <= 2; ++j ) {
						/* Fetch Attachment filenames for only the worklog number provided in the URL */
		                Value val = entryList.get(i).get( ATTACH_FIELDS[j] );
						AttachmentValue aval = (AttachmentValue)val.getValue();
						
						/* Attachment will be null if there is nothing stored */
		                if ( aval != null ) {
							String name = aval.getName();
							int slash = name.lastIndexOf( 0x5C ) + 1; 
							
							/* Parse out the full path name containing C:\Path\To\File (If it exists) */
							if ( slash > -1 ) {
								name = name.substring( slash ); 
							}
						
							returnString += "<a href=\"" + request.getRequestURL() + "/" + worklogId + "/" + j + "\" target=\"_blank\">" + name + "</a><br/>";
		                }
					}
					
				}
				
				/* Return list HTML */
	            response.reset();
                response.setContentType( "text/html" );
                
                PrintWriter output = response.getWriter();
				output.println( "<html><body bgcolor=" + BACKGROUND_COLOR + ">" );
                output.println( returnString );
				output.println(" </body></html>" );
                output.close();
			}
			else {
				if (DEBUG_ENABLED) {
					PrintError( response, "[AttachmentServlet] Error: No attachments found for ID: " + requestId );
				}
				else {
					PrintError( response, "<html><body bgcolor=" + BACKGROUND_COLOR + ">"  );
				}
			}
		}
		catch (ARException e) {
			server.logout();
			PrintWriter pw = response.getWriter();
			pw.println("[AttachmentServlet] " + e.getMessage());
			pw.close();
		}
		
		server.logout();
	}
	
	/* This function finds all Worklog entries for the provided INC/CRQ ID. Instead of listing the worklogs themselves,
	 * this function creates a list of Attachment links without regard to each specific Worklog. Therefore, if 3 worklogs
	 * have the Summary of WORKLOG_SUMMARY_SEARCH, a list of links to the attachments of all 3 worklogs will be generated.
	 *   @param HTTP Request Object - Used to determine the current hostname, URL, and web server port
	 *   @param HTTP Response Object - The attachment link list is printed to the response
	 *   @param Request ID - Either for INC or CRQ
	 */
	private void FetchWorkDetailsAttachmentsOnlyList ( HttpServletRequest request, HttpServletResponse response, String id ) throws IOException {
		com.bmc.arsys.api.ARServerUser server = new com.bmc.arsys.api.ARServerUser();
		server.setServer( ARS_HOSTNAME );
		server.setUser( API_AUTH_USER );
		server.setPassword( API_AUTH_PASS );
		server.setPort( ARS_PORT );
		String qualStr = "( \'" + QUAL_REQUEST_FIELD + "\' = \"" + id + "\" ) AND ( \'Description 2\' = \"" + WORKLOG_SUMMARY_SEARCH + "\" )";
		
		try {
			server.verifyUser();
		}
		catch (ARException e) {
			server.logout();
			PrintWriter pw = response.getWriter();
			pw.println( "[Verify User Error] " + e.getMessage() );
			pw.close();
		}
		try {
			
			List <Field> fields = server.getListFieldObjects( FORM_WORK_INFO );

			// Create the search qualifier.
			QualifierInfo qual = server.parseQualification(qualStr, fields, null, Constants.AR_QUALCONTEXT_DEFAULT);
			
			/* Fields: Worklog ID, Status, Summary, Notes, Request ID, # Attachments */
			int[] fieldIds = {1,7,1000003320,1000000182,1000000365};
			
			/* Integer to pass to getList function - Will be populated with the number of results found. */
			OutputInteger nMatches = new OutputInteger();
			
			List<SortInfo> sortOrder = new ArrayList<SortInfo>();
			sortOrder.add(new SortInfo(1, Constants.AR_SORT_ASCENDING));
			
			/* Grab all work details for the given INC ID */
			List<Entry> entryList = server.getListEntryObjects( FORM_WORK_INFO, qual, 0, Constants.AR_NO_MAX_LIST_RETRIEVE, sortOrder, fieldIds, true, nMatches);

			if ( nMatches.intValue() > 0 ) {
				
			    String returnString = "";
				int totalAttachCount = 1;
				
				/* Iterate through each returned Worklog Entry and grab the attachment info for each */ 
				for ( int i=0; i < nMatches.intValue(); ++i ) {
				
					/* Make sure attachment number isn't null (This is possible) */
					if ( entryList.get(i).get( 1000000365 ).getValue() != null ) {
				
						/* Iterate through number of attachments and generate links for each one */
						int num = entryList.get(i).get( 1000000365 ).getIntValue();
						for ( int j=1; j <= num; ++j ) {
							String worklogJoinId = entryList.get(i).get( 1 ).toString();
							String worklogId = worklogJoinId.substring( worklogJoinId.indexOf("|")+1 );
							returnString += "<a href=\"" + request.getRequestURL() + "/" + worklogId + "/" + j + "\" target=\"_blank\">Attachment " + totalAttachCount + "</a><br/>";
							++totalAttachCount;
						}
					}
				}
				
				/* Return list HTML */
	            response.reset();
                response.setContentType( "text/html" );
                
                PrintWriter output = response.getWriter();
				output.println( "<html><body bgcolor=" + BACKGROUND_COLOR + ">" );
                output.println( returnString );
				output.println(" </body></html>" );
                output.close();
			}
			else {
				if (DEBUG_ENABLED) {
					PrintWriter pw = response.getWriter();pw.println( "[AttachmentServlet] Error: No attachments found for ID: " + id  );pw.close();
				}
				else {
					PrintWriter pw = response.getWriter();pw.println( "<html><body bgcolor=" + BACKGROUND_COLOR + ">"  );pw.close();
				}
			}
		}
		catch (ARException e) {
			server.logout();
			PrintWriter pw = response.getWriter();
			pw.println("[AttachmentServlet] " + e.getMessage());
			pw.close();
		}
		
		server.logout();
	}
	
	/** 
     *  Parse the Configuration YML file
     */
    private Map<String,String> GetProperties() {
        Map<String,String> propertyMap = new HashMap<String,String>();
        try{
            FileInputStream yaml = new FileInputStream(getServletContext().getRealPath(CONFIG_FILE_PATH));
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
    
    private ARServerUser Login( HttpServletResponse response ) {
    	com.bmc.arsys.api.ARServerUser server = new com.bmc.arsys.api.ARServerUser();
		server.setServer( ARS_HOSTNAME );
		server.setUser( API_AUTH_USER );
		server.setPassword( API_AUTH_PASS );
		server.setPort( ARS_PORT );
		
		try {
			server.verifyUser();
		}
		catch (ARException e) {
			server.logout();
			PrintError( response, "[Verify User Error] " + e.getMessage() );
		}
		return server;
    }
    
    protected void PrintError( HttpServletResponse response, String error ) {
    	try {
	    	PrintWriter pw = response.getWriter();
	    	response.setContentType( "application/json" );
	        pw.println( "{\"Error\":\"" + error + "\"}" );
	        pw.close();
    	}
    	catch ( IOException e ) {
    		e.printStackTrace();
    	}
    }
}