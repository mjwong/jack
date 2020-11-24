import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class CodeWriter {
	private FileWriter fw;
	private StringBuilder sb;
	private String commentStr = "";
	private String currentFile = "";
	private String currentFn = "";	// current function name
	private int label  = 0;			// unique label for jump location. Increments each time it's called.
	private int rLabel = 0;			// unique label for fn return. Increments each time it's called.
	
	public CodeWriter(String filename) {
		try 
		{
			File fl = new File(filename);
			fw = new FileWriter(fl);
		}
		catch (IOException  e) { 
			e.printStackTrace();
		}
	}
	
	public void writeArithmetic(String command) {
		// Writes to the output file the assembly code that implements
		// the given arithmetic command.
		// add, sub, neg, eq, gt, lt, and, or, not
		
		int label1;
		sb = new StringBuilder();
		sb.append("// " + command  + "\n");
		
		switch (command) {
		case "add" : case "sub" : case "eq" : case "gt" : case "lt" : case "and" : case "or" :
			
			sb.append("@SP"		+ '\n');	// SP
			sb.append("AM=M-1" 	+ '\n');	// SP--
			sb.append("D=M" 	+ '\n');	// store 1st value
			sb.append("A=A-1" 	+ '\n');
			
			switch (command) {
			case "add" :
				sb.append("M=D+M" 	+ '\n');	// store result of 1st + 2nd value
				break;
				
			case "sub" :
				sb.append("M=M-D" 	+ '\n');	// store result of 2nd - 1st value
				// result of M: -1 if true, else 0.
				break;
				
			case "eq" : case "gt" : case "lt" :
				label1 = label++;
				
				sb.append("D=M-D" 	+ '\n');	// store result of 2nd - 1st value
				
				sb.append("@True" + label1 + '\n');	
				// if eq, then D=0 jump to label1 
				if (command.equals("eq"))
					sb.append("D, JEQ" 	+ '\n');
				else if (command.equals("gt"))
					sb.append("D, JGT" 	+ '\n');
				else if (command.equals("lt"))
					sb.append("D, JLT" 	+ '\n');
				
				sb.append("@SP"				   + '\n');
				sb.append("A=M-1" 		       + '\n');
				sb.append("M=0" 			   + '\n');		// store false (0) in SP
				sb.append("@Continue" + label1 + '\n');
				sb.append("0; JMP" 			   + '\n');
				
				sb.append("(True" + label1	   + ")\n");	// Label True
				sb.append("@SP"				   + '\n');
				sb.append("A=M-1"			   + '\n');
				sb.append("M=-1" 			   + '\n');		// store true (-1) in SP
				sb.append("(Continue" + label1 + ")\n");	// Label Continue
				break;
				
			case "and" :
				sb.append("M=D&M" 	+ '\n');	// store result of 1st && 2nd value
				break;
				
			case "or" :
				sb.append("M=D|M" 	+ '\n');	// store result of 1st || 2nd value
				break;
			}
			break;
		
		case "neg" : case "not" :
			sb.append("@SP"		+ '\n');	// SP
			sb.append("A=M-1" 	+ '\n');	// A = *SP-1
			
			switch (command) {
			case "neg" :
				sb.append("M=-M" 	+ '\n');	// store negated value
				break;
				
			case "not" :
				sb.append("M=!M" 	+ '\n');	// bitwise Not
				break;
			}	
			break;
		}
		
		writeFile();
	}
	
	public void WritePushPop(String command, String segment, int index) {
		// Writes to the output file the assembly code that implements
		// the given command, where command is either C_PUSH or C_POP.
		// pop segment i, push segment i
		commentStr = "// " + command + " " + segment + " " + index + "\n";
		
		switch (command) {
		case "C_PUSH" :
			
			switch (segment) {
			case "constant" :
				pushConstanti(index);
				break;
				
			case "local" : case "temp" : case "argument" : case "this" : case "that" :
				pushSegmenti(segment, index);
				break;
				
			case "pointer" :
				pushPointer(index);
				break;
				
			case "static" :
				pushStatic(index);
				break;
			}
			break;
			
		case "C_POP" :
			
			switch (segment) {
			case "local" : case "temp" : case "argument" : case "this" : case "that" :
				popSegmenti(segment, index);
				break;
				
			case "pointer" :
				popPointer(index);
				break;
				
			case "static" :
				popStatic(index);
				break;
			}
			break;
		}
		
		writeFile();
	}
	
	public void setFileName(String fileName) {
		// Informs the codeWriter that the translation of a new VM file
		// has started (called by the main program of the VM translator).
		this.currentFile = fileName;
	}
	
	public void writeInit() {
		// Writes the assembly instructions that effect the bootstrap code
		// that initializes the VM. This code must be placed at the beginning
		// of the generated *.asm file.
		sb = new StringBuilder();
		sb.append("// bootstrap\n");
		
		sb.append("@256" 	+ '\n');	// SP address
		sb.append("D=A" 	+ '\n');
		sb.append("@SP" 	+ '\n');
		sb.append("M=D" 	+ '\n');
		
		sb.append("@300" 	+ '\n');	// LCL address
		sb.append("D=A" 	+ '\n');
		sb.append("@LCL" 	+ '\n');
		sb.append("M=D" 	+ '\n');
		
		sb.append("@400" 	+ '\n');	// ARG address
		sb.append("D=A" 	+ '\n');
		sb.append("@ARG" 	+ '\n');
		sb.append("M=D" 	+ '\n');
		
		sb.append("@3000" 	+ '\n');	// THIS address
		sb.append("D=A" 	+ '\n');
		sb.append("@THIS" 	+ '\n');
		sb.append("M=D" 	+ '\n');
		
		sb.append("@3010" 	+ '\n');	// THAT address
		sb.append("D=A" 	+ '\n');
		sb.append("@THAT" 	+ '\n');
		sb.append("M=D" 	+ '\n');
		writeFile();
		
		writeCall("Sys.init", 0);
	}
	
	public void writeLabel(String label) {
		// Writes assembly code that effects the label command.
		String retLabel = getLabel(label);
		
		sb = new StringBuilder();
		sb.append("// label " + retLabel  + "\n");
		sb.append("(" + retLabel + ")\n");
		
		writeFile();
	}
	
	public void writeGoto(String label) {
		// Writes assembly code that effects the goto command.
		String retLabel = getLabel(label);
		
		sb = new StringBuilder();		
		sb.append("// goto " + retLabel + '\n');
		
		sb.append("@" + retLabel + '\n');
		sb.append("0; JMP" 		 + '\n');
		writeFile();
	}
	
	public void writeIf(String label) {
		// Writes assembly code that effects the if-goto command.
		// If condition != 0 goto label
		String retLabel = getLabel(label);
		
		sb = new StringBuilder();
		sb.append("// if-goto " + retLabel + '\n');
		
		sb.append("@SP" 		 + '\n');
		sb.append("AM=M-1"		 + '\n');	// SP--
		sb.append("D=M"			 + '\n');	// D = *SP. D=-1 if true
		sb.append("@" + retLabel + '\n');
		sb.append("D; JNE" 		 + '\n');
		writeFile();
	}
	
	private String getLabel(String label) {
		return currentFn + "$" + label;
	}
	
	public void writeFunction(String functionName, int numVars) {
		// Writes assembly code that effects the function command.
		int label1;		// use unique global label and increment
		String fnLbl  = functionName;
		
		currentFn = functionName;	// update global fn name for use in label

		sb = new StringBuilder();
		sb.append("// function " + functionName  + " " + numVars + "\n");
		sb.append("(" + fnLbl 	+ ")\n");	// declares label for function entry point
		
		if (numVars>0) {
			label1 = label++;					// use unique global label and increment
			sb.append("@" + numVars + '\n');	// no. of local variables
			sb.append("D=A"			+ '\n');	// D = nVars
			
			// pushes local variables initialized to 0
			sb.append("(Loop" + label1 + ")\n");
			sb.append("@SP" 		   + '\n');
			sb.append("A=M" 		   + '\n');
			sb.append("M=0"			   + '\n');	// push 0
			sb.append("@SP" 		   + '\n');
			sb.append("M=M+1"		   + '\n');	// SP++
			sb.append("D=D-1"		   + '\n');	// D--
			sb.append("@Loop" + label1 + '\n');	// global label variable which auto increments
			sb.append("D; JGT" 		   + '\n');		
		}
		else
			sb.append("// zero local var.\n");		// skip local variable allocation
		writeFile();
	}
	
	public void writeCall(String functionName, int numArgs) {
		// Writes assembly code that effects the call command.
		String fnLbl  = functionName;
		String retLbl = functionName + "$ret." + rLabel++;
		
		sb = new StringBuilder();
		sb.append("// call " + functionName + " " + numArgs + "\n");
		
		// saves ARG
		sb.append("@" + numArgs + '\n');
		sb.append("D=A"			+ '\n');	// D = nArgs
		sb.append("@SP" 		+ '\n');
		sb.append("D=M-D"		+ '\n');	// D = *SP - D
		sb.append("@R13" 		+ '\n');
		sb.append("M=D"			+ '\n');	// saves ARG in R13
		
		// save return address
		sb.append("@" + retLbl 	+ '\n');
		sb.append("D=A"			+ '\n');
		sb.append("@SP" 		+ '\n');
		sb.append("A=M" 		+ '\n');
		sb.append("M=D"			+ '\n');
		
		sb.append("@SP" 		+ '\n');
		sb.append("M=M+1"		+ '\n');	// SP++
		
		// save caller frame
		saveFrame("LCL");
		saveFrame("ARG");
		saveFrame("THIS");
		saveFrame("THAT");
		
		// sets ARG
		sb.append("@R13" 		+ '\n');
		sb.append("D=M"			+ '\n');
		sb.append("@ARG" 		+ '\n');
		sb.append("M=D"			+ '\n');
		
		// sets LCL = SP
		sb.append("@SP" 		+ '\n');
		sb.append("D=M" 		+ '\n');
		sb.append("@LCL" 		+ '\n');
		sb.append("M=D"			+ '\n');
		
		// jumps to function entry point
		sb.append("@" + fnLbl 	+ '\n');
		sb.append("0; JMP"		+ '\n');
		
		// generate return label
		sb.append("(" + retLbl 	+ ")\n");
		
		writeFile();
	}
	
	private void saveFrame(String baseAdr) {
		sb.append("@" + baseAdr + '\n');
		sb.append("D=M"			+ '\n');
		sb.append("@SP" 		+ '\n');
		sb.append("A=M" 		+ '\n');
		sb.append("M=D"			+ '\n');
		
		sb.append("@SP" 		+ '\n');
		sb.append("M=M+1"		+ '\n');	// SP++
	}
	
	public void writeReturn() {
		// Writes assembly code that effects the return command.
		
		sb = new StringBuilder();
		sb.append("// return\n");
		
		sb.append("@LCL"		+ '\n');
		sb.append("D=M"			+ '\n');	// D = LCL
		sb.append("@R13" 		+ '\n');	// R13 contains endframe address
		sb.append("M=D"			+ '\n');	// endframe = LCL
		
		// goto retaddr
		sb.append("@5"			+ '\n');
		sb.append("D=A"			+ '\n');
		sb.append("@R13" 		+ '\n');
		sb.append("A=M-D"		+ '\n');	// retaddr A = *endframe - 5
		sb.append("D=M" 		+ '\n');	// D = *retaddr
		sb.append("@R14" 		+ '\n');
		sb.append("M=D"			+ '\n');	// R14 contains return address
		
		// *ARG = pop(); copies return value onto argument 0
		sb.append("@SP" 		+ '\n');
		sb.append("AM=M-1"		+ '\n');	// SP--
		sb.append("D=M"			+ '\n');	// D = *SP
		
		sb.append("@ARG"		+ '\n');
		sb.append("A=M" 		+ '\n');
		sb.append("M=D"			+ '\n');	// *ARG = D
		
		// SP = ARG + 1; clears stack by setting SP for the caller
		sb.append("@ARG"		+ '\n');
		sb.append("D=M"			+ '\n');	// D = ARG
		sb.append("@SP" 		+ '\n');
		sb.append("M=D+1" 		+ '\n');	// SP = ARG + 1
		
		// restore segment pointers of the caller
		restoreFrame("THAT", 1);	// THAT = *(endframe - 1)
		restoreFrame("THIS", 2);	// THIS = *(endframe - 2)
		restoreFrame("ARG",  3);	// ARG  = *(endframe - 3)
		restoreFrame("LCL",  4);	// LCL  = *(endframe - 4)

		// goto retaddr
		sb.append("@R14" 		+ '\n');
		sb.append("A=M" 		+ '\n');	// A = *retaddr
		sb.append("0; JMP" 		+ '\n');	// goto retaddr
		writeFile();
	}
	
	private void restoreFrame(String baseLbl, int offset) {
		sb.append("@" + offset	+ '\n');
		sb.append("D=A"			+ '\n');
		sb.append("@R13" 		+ '\n');
		sb.append("A=M-D" 		+ '\n');
		sb.append("D=M"			+ '\n');
		sb.append("@" + baseLbl + '\n');
		sb.append("M=D"			+ '\n');	// LCL = *(endframe - 4)
	}
	
	private void pushConstanti(int index) {
		sb = new StringBuilder();
		sb.append(commentStr);
		
		sb.append("@" + String.valueOf(index) + '\n');	// @i
		sb.append("D=A" 	+ '\n');
		sb.append("@SP" 	+ '\n');
		sb.append("A=M" 	+ '\n');
		sb.append("M=D"		+ '\n');	// *SP = D
		sb.append("@SP"		+ '\n');	// SP++
		sb.append("M=M+1"	+ '\n');
	}
	
	private void pushSegmenti(String segment, int index) {
		sb = new StringBuilder();
		sb.append(commentStr);
		String baseAddr = getSegAddr(segment);

		// address = segmentPointer + i
		sb.append("@" + String.valueOf(index) + '\n');	// @i
		sb.append("D=A" 		 + '\n');	// D = i
		sb.append("@" + baseAddr + '\n');	// base address
		
		if (segment.equals("temp"))
			sb.append("A=D+A"	 + '\n');	// address = i + A
		else
			sb.append("A=D+M"	 + '\n');	// address = i + base address
		
		// *SP = *address
		sb.append("D=M" 		 + '\n');	// D = *address
		sb.append("@SP" 		 + '\n');	// SP
		sb.append("A=M" 		 + '\n');	// address = *SP
		sb.append("M=D" 		 + '\n');	// *SP = *address
		
		// SP++
		sb.append("@SP" 		 + '\n');
		sb.append("M=M+1" 		 + '\n');
	}
	
	private void pushPointer(int index) {
		// pointer 0 - THIS, pointer 1 - THAT
		
		sb = new StringBuilder();
		sb.append(commentStr);
		
		if (index == 0)
			sb.append("@THIS" 	 + '\n');	// select THIS
		else if (index == 1)
			sb.append("@THAT" 	 + '\n');	// select THAT
		
		sb.append("D=M" 		 + '\n');	// D = *pointer
		sb.append("@SP" 		 + '\n');	// SP
		sb.append("A=M" 		 + '\n');	// address = *SP
		sb.append("M=D" 		 + '\n');	// *SP = *pointer
		
		sb.append("@SP" 		 + '\n');
		sb.append("M=M+1" 		 + '\n');	// SP++
		
	}
	
	private void pushStatic(int index) {
		String sLabel = currentFile.split("\\.")[0] + "." + index;
		
		sb = new StringBuilder();
		sb.append(commentStr);
		
		sb.append("@" + sLabel + '\n');
		sb.append("D=M" 	   + '\n');
	
		sb.append("@SP" 	   + '\n');
		sb.append("A=M" 	   + '\n');
		sb.append("M=D"		   + '\n');
		
		sb.append("@SP" 		 + '\n');
		sb.append("M=M+1" 		 + '\n');	// SP++
	}
	
	private void popSegmenti(String segment, int index) {
		sb = new StringBuilder();
		sb.append(commentStr);
		String baseAddr = getSegAddr(segment);
		
		// address = segmentPointer + i
		sb.append("@" + String.valueOf(index) + '\n');	// @i
		sb.append("D=A" 					  + '\n');	// D = i
		
		sb.append("@" + baseAddr + '\n');	// base address
		
		if (segment.equals("temp"))
			sb.append("A=D+A"	 + '\n');
		else
			sb.append("A=D+M"	 + '\n');	// address = i + baseaddress
		
		sb.append("D=A" 		 + '\n');
		sb.append("@R13" 		 + '\n');	// select R13
		sb.append("M=D" 		 + '\n');	// store in R13
		
		// SP--
		sb.append("@SP" 		 + '\n');
		sb.append("AM=M-1" 		 + '\n');	// SP--
		
		// *address = *SP
		sb.append("D=M" 		 + '\n');	// D = *SP
		sb.append("@R13" 		 + '\n');	// select R13
		sb.append("A=M" 		 + '\n');	// address = *R13
		sb.append("M=D" 		 + '\n');	// *address = D
	}
	
	private void popPointer(int index) {
		// pointer 0 - THIS, pointer 1 - THAT
		
		sb = new StringBuilder();
		sb.append(commentStr);

		sb.append("@SP" 		 + '\n');
		sb.append("AM=M-1" 		 + '\n');	// SP--
		sb.append("D=M" 		 + '\n');	// D = *SP
		
		if (index == 0)
			sb.append("@THIS" 	 + '\n');	// select THIS
		else if (index == 1)
			sb.append("@THAT" 	 + '\n');	// select THAT
		
		sb.append("M=D" 		 + '\n');	// *pointer 0/1 = D
	}
	
	private void popStatic(int index) {
		String sLabel = currentFile.split("\\.")[0] + "." + index;
		
		sb = new StringBuilder();
		sb.append(commentStr);
		
		sb.append("@SP" 	   + '\n');
		sb.append("AM=M-1" 	   + '\n');	// SP--
		sb.append("D=M" 	   + '\n');
		
		sb.append("@" + sLabel + '\n');
		sb.append("M=D" 	   + '\n');
	}
	
	private String getSegAddr(String segment) {
		String str = null;
		
		if (segment.equals("local"))
			str = "LCL";
		else if (segment.equals("argument"))
			str = "ARG";
		else if (segment.equals("this"))
			str = "THIS";
		else if (segment.equals("that"))
			str = "THAT";
		else if (segment.equals("temp"))
			str = "R5";
		else if (segment.equals("static"))
			str = "16";
		
		return str;
	}
	
	private void writeFile() {
		// output to file
		try {
			fw.write(sb.toString());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void Close() {
		// Closes the output file
	   try {
			fw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}