import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.stream.Stream;

public class Parser {
	private Stream<String> stream;
	private Iterator<String> st;
	private String currentLine;
	private String currentCmd;
	
	public Parser(String filename) {
		// Opens the input file/stream and gets ready to parse it.

		try {
			stream = Files
					.lines(Paths.get(filename))
					.filter(p -> !p.matches("^(\\s*|//.*)$"));

		} catch (NoSuchFileException e) {
			System.out.println("File not found.");
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		st = stream.iterator();
	}
	
	public boolean hasMoreCommands() {
		// Checks whether there are more commands in the input
		
		if (st.hasNext())
			return true;
		else 
			return false;
	}
	
	public void advance() {
		// Reads the next command from the input and makes it the current command. 
		// Should be called only if hasMoreCommands() is true.
		// Initially there is no current command.
		String c = null;
		
		if (hasMoreCommands()) {
			c = st.next();
			// removes comments
	    	if (c.contains("//"))
	    		c = c.substring(0, c.indexOf("//")).trim();
		}
    	currentLine = c;
		currentCmd = currentLine.split("\\s+", 3)[0];
	}
	
	public String commandType() {
		// Returns a constant representing the type of the current command.
		// C_ARITHMETIC is returned for all arithmetic/logical commands.
		// C_ARITHMETIC, C_PUSH, C_POP, C_LABEL, C_GOTO, C_IF, C_FUNCTION, C_RETURN, C_CALL
		
		return getCmdType(currentCmd);
	}
	
	public String getCurrentCmd() {
		return currentCmd;
	}
	
	public String arg1() {
		// Returns the first argument of the current command.
		// In the case of C_ARITHMETIC, the command itself (add, sub, etc.) is returned.
		// Should not be called if the current command is C_RETURN.
		
		String arg = null;
		String[] c = currentLine.split("\\s+");
		
		if (c[0] != "return" && c[0].matches("push|pop|label|if-goto|goto|function|call"))
			arg = c[1].trim();
		return arg;
	}
	
	public int arg2() {
		// Returns the second argument of the current command.
		// Should be called only if the current command is 
		// C_PUSH, C_POP, C_FUNCTION, or C_CALL.
		// return -1 if there is no second argument.
		
		int arg = -1;
		String[] c = currentLine.split("\\s+");
		
		if (c[0].matches("push|pop|function|call"))
			arg = Integer.parseInt(c[2].trim());
		return arg;
	}
	
	private String getCmdType(String s) {
		String c = null;
		if (s.matches("^(add|sub|neg|eq|gt|lt|and|or|not)"))
			c = "C_ARITHMETIC";
		else if (s.matches("if-goto"))
			c = "C_IF";
		else if (s.matches("^(push|pop|label|goto|function|call|return)"))
			c = "C_" + s.toUpperCase();
		return c;
	}
}
