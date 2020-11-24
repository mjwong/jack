import java.io.File;

public class JackCompiler {
	
	public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java JackCompiler <filename.jack or dirname>");
            System.exit(-1);
        }
        
        String path 		= args[0];
		File file 			= new File(path);
		
        boolean exists 		= file.exists();      // Check if the file exists
        boolean isDirectory = file.isDirectory(); // Check if it's a directory
        boolean isFile 		= file.isFile();      // Check if it's a regular file
        
        if (exists) {
    		// open filename.jack or directoryName for writing, one xml for each jack file.
        	if (isDirectory) {
        		File[] directoryListing = file.listFiles();
        		if (directoryListing != null) {
        			for (File child : directoryListing) {
        				ProcessFile(child);
        			}
      		    }
        	}
        	else if (isFile) {
        		ProcessFile(file);
        	}
        	
    		//.Close();
        }
        else
        	System.out.println("File does not exist.");
	}
	
	private static void ProcessFile(File file) {
		String path = file.getPath();
		String 	ext = path.substring(path.lastIndexOf(".") + 1);
		
		if (!ext.toLowerCase().equals("jack")) return;
	
		CompilationEngine ce = new CompilationEngine(path);
	}
}
