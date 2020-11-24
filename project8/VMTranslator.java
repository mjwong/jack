import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.stream.Stream;

public class VMTranslator {

	public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java VMTranslator <filename.vm>");
            System.exit(-1);
        }
        
		CodeWriter cw 		= null;
        String path 		= args[0];
        String fileName		= null;
		File file 			= new File(path);
		
        boolean exists 		= file.exists();      // Check if the file exists
        boolean isDirectory = file.isDirectory(); // Check if it's a directory
        boolean isFile 		= file.isFile();      // Check if it's a regular file
        
        if (exists) {
    		// open filename.asm or directoryName.asm for writing
        	if (isDirectory)
            	fileName = file.getPath() + "/" + file.getName() + ".asm";
        	else {
        		fileName = file.getPath().substring(0, file.getPath().lastIndexOf(".")) + ".asm";
        	}
    		cw = new CodeWriter(fileName);
        	System.out.format("Output asm file written to %s\n", fileName);

        	if (isDirectory) {
        		File[] directoryListing = file.listFiles();
        		if (directoryListing != null) {
            		for(File child : directoryListing) {
            			if (child.getName().equals("Sys.vm") && FindSysInit(child)) {
            				System.out.println("Found Sys.vm & function Sys.init 0. Bootstrap included.");
                       		// write bootstrap code to beginning of .asm file
                    		cw.writeInit();
            				break;
            			}
            		}
            		
        			for (File child : directoryListing) {
        				ProcessFile(child, cw);
        			}
      		    }
        	}
        	else if (isFile) {
        		ProcessFile(file, cw);
        	}
        	
    		cw.Close();
        }
        else
        	System.out.println("File does not exist.");
	}
	
	private static boolean FindSysInit(File file) {
		BufferedReader br = null;
		String str = "^(function\\sSys\\.init\\s0)$";
		
		try {
			br = new BufferedReader(new FileReader(file));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		return (br.lines().filter(p -> p.matches(str)).count() != 0);
	}
	
	private static void ProcessFile(File file, CodeWriter cw) {
		String 	filePath = file.getPath();
		String 	ext 	 = filePath.substring(filePath.lastIndexOf(".") + 1);
		
		if (!ext.toLowerCase().equals("vm")) return;
		
		// parse input filename.vm
		Parser parse = new Parser(filePath);
		
		// informs that the translation of new VM file has started
		cw.setFileName(file.getName());
		
		// process each line
		while (parse.hasMoreCommands()) {
			parse.advance();
			String cmd = parse.getCurrentCmd().split("\\+")[0];
			String cmdType = parse.commandType();
			
			switch (cmdType) {
			case "C_PUSH" : case "C_POP" :
				cw.WritePushPop(cmdType, parse.arg1(), parse.arg2());
				break;
			case "C_ARITHMETIC" :
				cw.writeArithmetic(cmd);
				break;
			case "C_LABEL" :
				cw.writeLabel(parse.arg1());
				break;
			case "C_GOTO" :
				cw.writeGoto(parse.arg1());
				break;
			case "C_IF" :
				cw.writeIf(parse.arg1());
				break;
			case "C_FUNCTION" :
				cw.writeFunction(parse.arg1(), parse.arg2());
				break;
			case "C_RETURN" :
				cw.writeReturn();
				break;
			case "C_CALL" :
				cw.writeCall(parse.arg1(), parse.arg2());
				break;
			};
		}
	}
}
