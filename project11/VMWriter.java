import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class VMWriter {
	private FileWriter fw 		= null;
	
	/** Creates a new output .vm file and prepares it for writing.
	 */
	public VMWriter(String path) {
		
		File fl = new File(path.substring(0, path.lastIndexOf(".")) + ".vm");
		
		try {
			fw = new FileWriter(fl);
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/** Writes a VM push command. 
	 *  Segment: ARG, LOCAL, STATIC, THIS, THAT, POINTER, TEMP
	 * @param segment
	 * @param index
	 */
	public void writePush(String segment, int index) {
		writeVM("push " + segment + " " + index);
	}
	
	/** Writes a VM ppp command.
	 * @param segment
	 * @param index
	 */
	public void writePop(String segment, int index) {
		writeVM("pop " + segment + " " + index);
	}
	
	/** Writes a VM arithmetic-logical command.
	 * Command: ADD, SUB, NEG, EQ, GT, LT, AND, OR, NOT
	 * @param command
	 */
	public void writeArithmetic(String command) {
		String op = null;
		
		switch (command) {
		case "+":
			op = "add";
			break;
			
		case "-":
			op = "sub";
			break;
			
		case "=":
			op = "eq";
			break;
			
		case ">":
			op = "gt";
			break;
			
		case "<":
			op = "lt";
			break;
			
		case "&":
			op = "and";
			break;
			
		case "|":
			op = "or";
			break;
			
		// using OS Lib
		case "*":
			op = "call Math.multiply 2";
			break;
			
		case "/":
			op = "call Math.divide 2";
			break;
			
		// unary
		case "u-":
			op = "neg";
			break;
			
		case "~":
			op = "not";
			break;
		}
		
		writeVM(op);
	}
	
	/** Writes a VM label command
	 * @param label
	 */
	public void writeLabel(String label) {
		writeVM("label " + label);
	}
	
	/** Writes a VM goto command.
	 * @param label
	 */
	public void writeGoto(String label) {
		writeVM("goto " + label);
	}
	
	/** Writes a VM if-goto command.
	 * @param label
	 */
	public void writeIf(String label) {
		writeVM("if-goto " + label);
	}
	
	/** Writes a VM call command.
	 * @param label
	 * @param nArgs
	 */
	public void writeCall(String name, int nArgs) {
		writeVM("call " + name + " " + String.valueOf(nArgs));
	}
	
	/** Writes a VM function command.
	 * @param name
	 * @param nLocals
	 */
	public void writeFunction(String name, int nLocals) {
		writeVM("function " + name + " " + String.valueOf(nLocals));
	}
	
	/** Writes a VM return command.
	 */
	public void writeReturn() {
		writeVM("return");
	}
	
	/** Closes the output file
	 */
	public void close() {
		try {
			fw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/** Writes the VM command to output file
	 */
	public void writeVM(String cmd) {
		try {
			fw.write(cmd + '\n');
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
}
