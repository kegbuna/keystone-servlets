/* KEYSTONE SERVLET BASE CLASS -------------------------------------------------------------
   Author: Ryan Ahern
   Date: June 1, 2012
   
   This class is meant to be the base class for all Keystone Servlets. All common
   functionality is placed into this class and is accessible from each Keystone servlet.
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


public class KeystoneServletBase extends HttpServlet {
	// Constants ----------------------------------------------------------------------------------
    private String SERVLET_NAME    				= "[KeystoneServletBase]";
    private String CONFIG_FILE_PATH 			= "/config/context.yml";
	private String ARS_HOSTNAME;
	private String API_AUTH_USER;
	private String API_AUTH_PASS;
	private String API_AUTH_COMPANY; /* Temp to be used for a qual */
	private int    ARS_PORT           			= 0;
	private String ARS_FORM_NAME				= "";
	private DateFormat DATE_FORMAT 				= new SimpleDateFormat("MM/dd/yyyy hh:mm a");
    private Map<String,String> CONFIG_MAP  		= new HashMap<String,String>();
    private Map<String,String> PARAM_MAP   		= new HashMap<String,String>();
    protected Map<Integer,String> STATUS_MAP 	= new HashMap<Integer,String>();
    protected Map<Integer,String> URGENCY_MAP 	= new HashMap<Integer,String>();
    protected Map<Integer,String> IMPACT_MAP 	= new HashMap<Integer,String>();
    
	public KeystoneServletBase() {
		super();
	}
	
	/**
	 * Builds all maps that convert AR System data to human-readable formatting.
	 */
	public void BuildMaps() { 
	    /* Need a better way to configure this */
	    STATUS_MAP.put(0, "New");
	    STATUS_MAP.put(1, "Assigned");
	    STATUS_MAP.put(2, "In Progress");
	    STATUS_MAP.put(3, "Pending");
	    STATUS_MAP.put(4, "Resolved");
	    STATUS_MAP.put(5, "Closed");
	    STATUS_MAP.put(6, "Cancelled");
	    
	    URGENCY_MAP.put(1000, "1-Critical");
	    URGENCY_MAP.put(2000, "2-High");
	    URGENCY_MAP.put(3000, "3-Medium");
	    URGENCY_MAP.put(4000, "4-Low");
	    
	    IMPACT_MAP.put(1000, "1-Extensive/Widespread");
	    IMPACT_MAP.put(2000, "2-Significant/Large");
	    IMPACT_MAP.put(3000, "3-Moderate/Limited");
	    IMPACT_MAP.put(4000, "4-Minor/Localized");
    }
	
	protected void ClearParamMap() {
		PARAM_MAP.clear();
	}
	
	protected String ConvertTimestamp( Value val ) {
        Timestamp ts = (Timestamp)val.getValue();
        Date sub_date_s = ts.toDate();
        return DATE_FORMAT.format(sub_date_s);
    }
	
    /**
     * Forward all requests over to doPost().
     * NOTE: doPost() must be overridden in the derived class.
     */
    protected void doGet( HttpServletRequest request, HttpServletResponse response ) throws ServletException, IOException {
		doPost( request, response );
	}
    
    protected String GetConfig( String name ) {
        String value = CONFIG_MAP.get( name );
        if ( value != null ) {
            return value;
        }
        return "";
    }
    
    protected String GetForm() {
    	return ARS_FORM_NAME;
    }
    
    protected String GetParameter( String name ) {
        String value = PARAM_MAP.get( name );
        if ( value != null ) {
            return value;
        }
        return "NONE";
    }
    
    protected Iterator GetParameterIterator() {
        return PARAM_MAP.entrySet().iterator();
    }
    
    protected int GetParameterMapSize() {
    	return PARAM_MAP.size();
    }
    
	/** 
     *  Parse the Configuration YML file
     */
    protected void GetProperties() {
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
                    	CONFIG_MAP.put(pair[0].trim(), pair[1].trim());
                    } else {
                    	CONFIG_MAP.put(pair[0].trim(), null);
                    }
                }
            }
            in.close();
        }
        catch ( Exception e ) {
        	CONFIG_MAP.put( "ERROR", e.getMessage() );
            e.printStackTrace();
        }
    }
	
    protected String GetServletName() {
        return SERVLET_NAME;
    }
    
    protected void ParseParameters( HttpServletRequest request ) {
        Enumeration paramNames = request.getParameterNames();

	    while ( paramNames.hasMoreElements() ) {
	        String paramName = (String)paramNames.nextElement();

	        /* Get the param value */
	        String paramValue = request.getParameter( paramName );
	        PARAM_MAP.put( paramName, paramValue );
	    }
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
    
    protected void SetForm( String form ) {
    	ARS_FORM_NAME = form;
    }
    
    protected void SetServletName( String name ) {
        SERVLET_NAME = "[" + name + "]";
    }
}