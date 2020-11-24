import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CompilationEngine {
	private JackTokenizer jt	= null;
	private SymbolTable   st	= null;
	private VMWriter	  vw	= null;
	private FileWriter    fw 	= null;
	private int indent			= 0;		// xml output indentation
	private int lblNo			= 0;		// class level unique label no. Auto-increment
	private int expList			= 0;		// no. of ',' separated expressions in subroutine call
	private boolean debug		= true;		// turns on console debugging output
	private String objName;
	private String className;
	private String subKeyWord;
	private String subName;
	private String subType;
	private String tokenKind;
	private String tokenType;
	private String term			= null;
	
	private final String ANSI_RESET 	= "\u001B[0m";	// console terminal color
	private final String ANSI_GREEN 	= "\u001B[32m";
	private final String ANSI_YELLOW 	= "\u001B[33m";
	private final String ANSI_BLUE 		= "\u001B[34m";
	private final String ANSI_CYAN 		= "\u001B[36m";
	
	/** Creates a new compilation engine with the given input and output.
	 *  THe next routing called must be compileClass.
	 */
	public CompilationEngine(String path) {
		
		jt = new JackTokenizer(path);
		
		st = new SymbolTable();
		
		vw = new VMWriter(path);
		
		File fl = new File(path.substring(0, path.lastIndexOf(".")) + ".xml");
		
		try {
			fw = new FileWriter(fl);
			
			if ( jt.hasMoreTokens() )
				jt.advance();
			
			compileClass();
			
			fw.close();
			
			st.Show();
			
			vw.close();
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/** Compiles a complete class.
	 *  Syntax: 'class' className '{' classVarDec* subroutineDec* '}'
	 */
	public void compileClass() {
		writeToken("<class>");
		indent++;
		
		writeTokenAdv("class");		// class
			
		className = writeTokenAdv2("-i");	// className
		
		vw.writeVM("// class " + className);
		
		writeTokenAdv("\\{");		// {
		
		while ( tokenIs("static|field") )
			compileClassVarDec();
		
		while ( tokenIs("constructor|method|function") )
			compileSubroutine();
		
		writeTokenAdv("\\}");		// }
		
		indent--;
		writeToken("</class>");
	}
	
	/** Compiles a static declaration or a field declaration.
	 *  Process additional tokens until it encounters a symbol ';'.
	 *  Then advances over to next token.
	 *  Syntax: field|static keyword|identifier identifier ;
	 *  Keyword must be either int, boolean or char.
	 *  Syntax: ('static'|'field') type varName (',' varName)* ';'
	 */
	public void compileClassVarDec() {
		
		writeLine("<classVarDec>");
		indent++;
		
		vw.writeVM("\n// " + readStatement(";"));
		
		tokenKind = writeTokenAdv2("static|field");
			
		compileDec();
		
		indent--;
		writeLine("</classVarDec>");
	}
	
	/** Compiles a var declaration.
	 *  Syntax: 'var' type varName (',' varName)* ';'
	 */
	public void compileVarDec() {
		
		writeLine("<varDec>");
		indent++;
		
		tokenKind = "local";
		
		vw.writeVM("\n// " + readStatement(";"));
		
		writeTokenAdv("var");
			
		compileDec();
		
		indent--;
		writeLine("</varDec>");
	}
	
	/** Compiles a local or class var declaration statement
	 *  Used by compileClassVarDec and compileVarDec
	 *  Syntax: type varName (',' varName)* ';'
	 */
	private void compileDec() {
		
		if ( isaType2() ) {
			tokenType = getTokenVal();
			writeTokenAdv(null);		//  type
		}
		
		writeVMVar();
		
		writeSymbolEntryAdv();
		//writeTokenAdv("-i");			// varName
		
		while ( tokenIs("\\,") ) {
			writeTokenAdv("\\,");		// ,
			
			writeVMVar();
			
			writeSymbolEntryAdv();
			//writeTokenAdv("-i");		// varName
		}
	
		writeTokenAdv("\\;");			// ;
	}
	
	/** Stores in symbol table and generates VM code
	 * @param name
	 */
	private void writeVMVar() {
		String name = getTokenVal();	// varName
		
		st.define(name, tokenType, tokenKind);
		st.Show();
	}
	
	/** Compiles a complete method, function, or constructor.
	 *  Syntax: (constructor|function|method)  (void|type) subroutineName ( parameterList ) subroutineBody
	 *  e.g. constructor Square new(int x, int y, int size) { statements; }
	 */
	public void compileSubroutine() {
		
		writeLine("<subroutineDec>");
		indent++;
		
		vw.writeVM("\n// " + readStatement("{"));
		
		subKeyWord = writeTokenAdv2("constructor|function|method");
		
		subType = writeTokenAdv2("-t");
		
		subName = writeTokenAdv2("-i");	// subroutineName
		
		st.startSubroutine();
		
		// methods with k arguments compiled to operate on k+1 arguments
		if (subKeyWord.matches("method"))
			st.define("this", subName, "argument");
		
		writeTokenAdv("\\(");			// (
		
		compileParameterList();
		
		writeTokenAdv("\\)");			// )
		
		compileSubroutineBody();
		
		indent--;
		writeLine("</subroutineDec>");
	}
	
	/** Compiles a (possibly empty) parameter list, not including the enclosing "()".
	 *  syntax: ( (type varName) ( ',' type varName)* )?
	 *  type: keyword is (char|int|boolean), identifier is built-in class type
	 */
	public void compileParameterList() {
		String name = null;
		
		writeLine("<parameterList>");
		indent++;
		
		tokenKind = "argument";
		
		if (! tokenIs("\\)") ) {
			
			tokenType = writeTokenAdv2("-t2");		// type
			
			name = writeTokenAdv2("-i");			// varName
			
			st.define(name, tokenType, tokenKind);
			
			while ( tokenIs("\\,") ) {
				
				writeTokenAdv("\\,");				// ,
				
				tokenType = writeTokenAdv2("-t2");	// type
				
				name = writeTokenAdv2("-i");		// varName
				
				st.define(name, tokenType, tokenKind);
			}
		}
		
		indent--;
		writeLine("</parameterList>");
	}
	
	/** Compiles a subroutine body, including { }.
	 *  Syntax: '{' (varDec)* statements '}'
	 *  Keeps track of no. of opening and closing braces.
	 */
	private void compileSubroutineBody() {
		
		writeLine("<subroutineBody>");
		indent++;
		
		writeTokenAdv("\\{");
		
		while ( tokenIs("var") )
			compileVarDec();
		
		vw.writeFunction(className + "." + subName, st.VarCount("local"));
		
		if (subKeyWord.equals("method")) {
			vw.writePush("argument", 0);
			
			vw.writePop("pointer", 0);
		}
		else if (subKeyWord.equals("constructor"))
			compileConstructorAlloc();

		compileStatements();
		
		writeTokenAdv("\\}");
		
		indent--;
		writeLine("</subroutineBody>");
	}
	
	/** Compiles construct code for new object creation and memory allocation
	 */
	private void compileConstructorAlloc() {
		int nFields = st.VarCount("field");
		
		if (nFields == 0)
			return;
		
		vw.writePush("constant", st.VarCount("field"));
		
		vw.writeCall("Memory.alloc", 1);
	
		vw.writePop("pointer", 0);
	}
	
	/** Compiles a sequence of statements, not
	 *  including the enclosing "{}".
	 *  Syntax: statement*
	 *  Statement prefix: let | if | while | do | return
	 */
	public void compileStatements() {
		
		writeLine("<statements>");
		indent++;
		
		while ( tokenIs("let|do|while|if|return") ) {
			switch(jt.keyWord()) {
			
			case JackTokenizer.DO :
				compileDo();
				break;
				
			case JackTokenizer.LET :
				compileLet();
				break;
				
			case JackTokenizer.WHILE :
				compileWhile();
				break;
				
			case JackTokenizer.RETURN :
				compileReturn();
				break;
				
			case JackTokenizer.IF :
				compileIf();
				break;
				
			default:
				break;
			}
		}
		
		indent--;
		writeLine("</statements>");
	}
	
	/** Compiles a do statement.
	 *  Syntax: 'do' subroutineCall ';'
	 */
	public void compileDo() {
		
		writeLine("<doStatement>");
		indent++;
		
		vw.writeVM("\n// " + readStatement(";"));
		
		writeTokenAdv("do");
		
		compileSubroutineCall();
		
		vw.writePop("temp", 0);		// drop the returned value for do statements
		
		writeTokenAdv("\\;");
		
		indent--;
		writeLine("</doStatement>");
	}
	
	/** Compiles a subroutine call expression
	 *  Syntax: subroutineName '(' expressionList ')' | 
	 *              (className | varName) '.' subroutineName '(' expressionList ')'
	 */	
	private void compileSubroutineCall() {
		
		objName = writeTokenAdv2("-i");	// subroutineName or (className or varName)
		
		if (tokenIs("\\(") ) {
			
			writeTokenAdv("\\(");	// (
			
			vw.writePush("pointer", 0);		// pushes THIS
			
			expList = 0;
			
			compileExpressionList();
			
			writeTokenAdv("\\)");	// )

			vw.writeCall(className + "." + objName, expList + 1);
		}
		else if (tokenIs("\\.") ) {
			
			writeTokenAdv("\\.");	// .
			
			if (! st.KindOf(objName).equals("NONE") )
				pushVar(objName);
			
			subName = writeTokenAdv2("-t");	// subroutineName
			
			writeTokenAdv("\\(");	// (
			
			expList = 0;
			
			compileExpressionList();
			
			writeTokenAdv("\\)");	// )
			
			if (! st.TypeOf(objName).equals("NONE"))
				vw.writeCall(st.TypeOf(objName) + "." + subName, expList + 1);	// method call with obj as arg 0
			else
				vw.writeCall(objName + "." + subName, expList);
		}
	}
	
	/** Compiles a let statement.
	 *  Syntax: 'let' varName ( '[' expression ']' )? '=' expression ';'
	 */
	public void compileLet() {
		String varName = null;
		boolean isArray = false;
		
		writeLine("<letStatement>");
		indent++;
		
		vw.writeVM("\n// " + readStatement(";"));
		
		writeTokenAdv("let");			// let
		
		varName = writeTokenAdv2("-i");	// varName
		
		if ( tokenIs("\\[") ) {
			
			pushVar(varName);
			
			writeTokenAdv("\\[");		// [
			
			compileExpression();		// expression1
			
			writeTokenAdv("\\]");		// ]
			
			vw.writeArithmetic("+");
			
			isArray = true;
		}
			
		writeTokenAdv("\\=");			// =
			
		compileExpression();			// expression2
			
		writeTokenAdv("\\;");			// ;
		
		if (isArray) {
			vw.writePop("temp", 0);			// temp 0 = value of expression2	
			
			vw.writePop("pointer", 1);		// store arr[expression1]
			
			vw.writePush("temp", 0);
			
			vw.writePop("that", 0);
		}
		else {
			popVar(varName);
		}
		
		indent--;
		writeLine("</letStatement>");
	}
	
	/** Compiles a while statement.
	 *  Syntax: while ( expression ) { statements }
	 */
	public void compileWhile() {
		int LFalse = lblNo++;
		int LTrue  = lblNo++;
		
		writeLine("<whileStatement>");
		indent++;
		
		vw.writeVM("\n// " + readStatement("{"));
		
		writeTokenAdv("while");		// while
		
		vw.writeLabel("L" + LTrue);
		
		writeTokenAdv("\\(");		// (
		
		compileExpression();
		
		writeTokenAdv("\\)");		// )
		
		vw.writeArithmetic("~");	// not
		
		vw.writeIf("L" + LFalse);
		
		writeTokenAdv("\\{");		// {
		
		compileStatements();
		
		writeTokenAdv("\\}");		// }
		
		vw.writeGoto("L" + LTrue);
		
		vw.writeLabel("L" + LFalse);
		
		indent--;
		writeLine("</whileStatement>");
	}
	
	/** Compiles a return statement.
	 *  Syntax: 'return' expression? ';'
	 *  return statement can occur anywhere, not necessary the last statement in subroutine body
	 */
	public void compileReturn() {
		
		writeLine("<returnStatement>");
		indent++;
		
		writeTokenAdv("return");	// return
		
		if (! tokenIs("\\;") )
			compileExpression();
		
		writeTokenAdv("\\;");		// ;
		
		if (subType.equals("void"))
			vw.writePush("constant", 0);
		
		vw.writeReturn();
		
		indent--;
		writeLine("</returnStatement>");
	}
		
	/** Compiles a if statement,
	 *  possibly with a trailing else clause.
	 *  Syntax: 'if' '(' expression ')' '{' statements '}' ( 'else' '{' statements '}' )?
	 */
	public void compileIf() {
		int LFalse = lblNo++;
		int LEnd   = lblNo++;
		
		writeLine("<ifStatement>");
		indent++;
		
		vw.writeVM("\n// " + readStatement("{"));
		
		writeTokenAdv("if");		// if
		
		writeTokenAdv("\\(");		// (
		
		compileExpression();
		
		writeTokenAdv("\\)");		// )
		
		vw.writeArithmetic("~");	// not
		
		vw.writeIf("L" + LFalse);
		
		writeTokenAdv("\\{");		// {
		
		compileStatements();
		
		writeTokenAdv("\\}");		// }
		
		vw.writeGoto("L" + LEnd);
		
		vw.writeLabel("L" + LFalse);
		
		if ( tokenIs("else") ) {
			
			writeTokenAdv("else");	// else
			
			writeTokenAdv("\\{");	// {
			
			compileStatements();
			
			writeTokenAdv("\\}");	// }
		}
		
		vw.writeLabel("L" + LEnd);
		
		indent--;
		writeLine("</ifStatement>");
	}
	
	/** Compiles a (possibly empty) comma-separated list of expressions.
	 *  Syntax: ( expression (',' expression)* )?
	 *  Called by subroutineCall()
	 */
	public void compileExpressionList() {
		
		writeLine("<expressionList>");
		indent++;
		
		if (! tokenIs("\\)") ) {
			compileExpression();
			
			expList++;
			
			while ( tokenIs("\\,")) {
				
				writeTokenAdv("\\,");		// ,
				
				compileExpression();
				
				expList++;
			}
		}
		
		indent--;
		writeLine("</expressionList>");
	}
	
	/** Compiles an expression.
	 *  Syntax: term (op term)*
	 */
	public void compileExpression() {
		String op = null;
		
		writeLine("<expression>");
		indent++;
		
		compileTerm();
		
		while ( isOp() ) {
			op = writeTokenAdv2(null);	// op
			
			compileTerm();
			
			vw.writeArithmetic(op);
		}
		
		indent--;
		writeLine("</expression>");
	}
	
	/** Checks whether the operator is valid
	 * @return boolean
	 */
	private boolean isOp() {
		String sym   = null;
		String opStr = "+-*/&|<>=";
		
		if ( ! (jt.tokenType() == JackTokenizer.SYMBOL))
			return false;
		
		sym = jt.symbol() + "";
		
		if (opStr.contains(sym))
			return true;
		else
			return false;
	}
	
	/** Compiles a term. This routine is faced with a slight difficulty 
	 *  when trying to decide between some of the alternative parsing rules.
	 *  Specifically, if the current token is an identifier, the routine must
	 *  distinguish between a variable, an array entry, and a subroutine call.
	 *  A single look-ahead token, which may be one of "[", "(", or "." suffices
	 *  to distinguish between the three possibilities. Any other token is not part
	 *  of this term and should not be advanced over.
	 *  Syntax: integerConstant | stringConstant | keywordConstant | varName |
	 *              varName '[' expression ']' | subroutineCall | '(' expression ')' | unaryOp term
	 *
	 *  Syntax of subroutineCall: subroutineName '(' expressionList ')' | 
	 *              (className | varName) '.' subroutineName '(' expressionList ')'
	 *   
	 *  varName requires look ahead but will not consume additional token if it is just a simple term.
	 */
	public void compileTerm() {
		
		writeLine("<term>");
		indent++;
		
		switch (jt.tokenType()) { 
		
		case JackTokenizer.INT_CONST:
			term = writeTokenAdv2(null);
			vw.writePush("constant", Integer.parseInt(term));
			break;
			
		case JackTokenizer.STRING_CONST:
			term = writeTokenAdv2(null);
			strConst(term);
			break;
			
		case JackTokenizer.KEYWORD:
			if ( tokenIs("true|false|null|this")) {
				term = writeTokenAdv2(null);
				switch (term) {
				case "true" :
					vw.writePush("constant", 1);
					vw.writeVM("neg");
					break;
				case "false" : case "null" :
					vw.writePush("constant", 0);
					break;
				case "this" :
					vw.writePush("pointer", 0);	// e.g. called by constructor return this;
					break;
				}
			}			
			break;
				
		case JackTokenizer.IDENTIFIER:
			compileTermLookAhead();
			break;
			
		case JackTokenizer.SYMBOL:		// ( or unary op
			processSym();
			break;
		
		default:
			break;
		}
		
		indent--;
		writeLine("</term>");
	}
	
	/** Process the string assignment and calling OS constructor String.new(length)
	 *  String assignments are handled using a series f calls to String.appendChar(c)
	 */
	private void strConst(String s) {
		int len = s.length();
		char [] arr = s.toCharArray();
		
		vw.writePush("constant", len + 1);		// String.new(length)
		
		vw.writeCall("String.new", 1);
		
		for (int i = 0; i < len; i++) {
			vw.writePush("constant", (int) arr[i]);
			
			vw.writeCall("String.appendChar", 2);
		}
		
		vw.writePush("constant", 32);			// null char
		
		vw.writeCall("String.appendChar", 2);
	}
	
	/** processes the identifier and look ahead 1 token
	 *  Uses peekToken() to peek at next token; it does not advance to next token.
	 */
	private void compileTermLookAhead() {
		
		String nextToken = getNextTokenVal();	// look ahead 1 token
		
		if ( nextToken.equals("[") )
			compileArray();
		
		else if ( nextToken.equals("(") || nextToken.equals(".") )
			compileSubroutineCall();
		
		else {
			term = writeTokenAdv2("-i");		// varName
			pushVar(term);
		}
	};
	
	/** compiles array term of the form varName[expression]
	 */
	private void compileArray() {
		
		String varName = null;
		
		varName = writeTokenAdv2("-i");	// identifier
		
		pushVar(varName);
		
		writeTokenAdv("\\[");			// [
		
		compileExpression();
		 
		writeTokenAdv("\\]");			// ]
		
		vw.writeArithmetic("+");
		
		vw.writePop("pointer", 1);		// set pointer 1 to (arr + value of expression)
		
		vw.writePush("that", 0);		// access array
	}
	
	/** Pushes a var. Checks if it is a field var. Format: push this i
	 */
	private void pushVar(String varName) {
		if (st.KindOf(varName).equals("field"))
			vw.writePush("this", st.IndexOf(varName));
		else
			vw.writePush(st.KindOf(varName), st.IndexOf(varName));
	}
	
	/** pop a var. Checks if it is a field var. Format: pop this i
	 */
	private void popVar(String varName) {
		if (st.KindOf(varName).equals("field"))
			vw.writePop("this", st.IndexOf(varName));
		else
			vw.writePop(st.KindOf(varName), st.IndexOf(varName));
	}
	
	/** processes either the format: '(' expression ')' or ('~'|'-') Term
	 */
	private void processSym() {
		String op = null;
		
		if ( tokenIs("\\(")) {
			
			writeTokenAdv("\\(");	// (
			
			compileExpression();
			
			writeTokenAdv("\\)");	// )
		}
		else if ( tokenIs("\\~")) {
			
			op = writeTokenAdv2("\\~");	// ~
			
			compileTerm();
			
			vw.writeArithmetic(op);
		}
		else if ( tokenIs("\\-")) {
			
			writeTokenAdv2("\\-");	// -
			
			compileTerm();
			
			vw.writeArithmetic("u-");
		}	
	}
	
	/** Read let/do/var/field statements. Stops at ';'
	 *  @return String
	 */
	private String readStatement(String last) {
		StringBuilder s = new StringBuilder();
		int step 		= 0;
		String tokXml   = null;
		String tokVal 	= getTokenVal();
		
		while (!tokVal.equals(last)) {
			tokXml = jt.peekToken(step++);
			tokVal = jt.xmlToString(tokXml).trim();
			if ( getTokenType(tokXml).equals("stringConstant") )
				tokVal = '"' + tokVal + '"';
			s.append( tokVal + " " );
		}
		return s.toString();
	}
	
	/** Is a type: int, char, boolean, void, built-in className
	 * @return boolean
	 */
	private boolean isaType() {
		if (tokenIs("int|char|boolean|void") || jt.tokenType() == JackTokenizer.IDENTIFIER)
			return true;
		return false;
	}
	
	/** Is a type: int, char, boolean, built-in className. Exclude void
	 * @return boolean
	 */
	private boolean isaType2() {
		if (tokenIs("int|char|boolean") || jt.tokenType() == JackTokenizer.IDENTIFIER)
			return true;
		return false;
	}
	
	/** Gets current calling method's name. For debugging
	 * @return String
	 */
	private static String getCurrentMethodName(int t) {
	    return Thread.currentThread().getStackTrace()[t].getMethodName();
	}
	
	/** Gets line no. in the token list file filenameT.xml
	 * @return int
	 */
	private int lineNo() {
		return (jt.getTokenPos() + 1);
	}
	
	/** Writes indentation at beginning of line
	 * @return string
	 */
	private String indents() {
		return new String(new char[indent]).replace("\0", "  ");
	}
	
	/** Writes indentation at beginning of line for debugging output
	 * @return string
	 */
	private String indentD() {
		return ANSI_BLUE + new String(new char[indent]).replace("\0", "| ") + ANSI_RESET;
	}
	
	/** Returns the token type of given token xml
	 * @return string
	 */
	public String getTokenType(String xml) {
		final Matcher m = Pattern.compile("\\<([^>]*)\\>").matcher(xml);
	    if (m.find())
	        return m.group(1); // first expression from round brackets (Testing)
	    else
	    	return null;
	}
	
	/** Token val is a ?
	 * @return boolean
	 */
	private boolean tokenIs(String s) {
		if ( getTokenVal().matches(s) )
			return true;
		return false;
	}
	
	/** Returns the value of given token
	 * @return string
	 */
	private String getTokenVal() {
		return jt.xmlToString(jt.getToken()).trim();
	}
	
	/** Returns the value of token at one position ahead.
	 * @return string
	 */
	private String getNextTokenVal() {
		return jt.xmlToString(jt.peekToken(1)).trim();
	}
	
	/** Prints value of current token. For debugging
	 * @return string
	 */
	private void printCurrentTokenVal() {
		if (debug)
			System.out.format("%s%s%d. Info  %s : %s%s%s\n", 
				indentD(), ANSI_GREEN, lineNo(), 
				getCurrentMethodName(4), ANSI_YELLOW, getTokenVal(),
				ANSI_RESET);
	}
	
	private void printErrMsg(String s) {
		System.out.format("%s%s%d. Error %s : Expected %s but got a %s.%s\n",
				indents(), ANSI_CYAN, lineNo(), getCurrentMethodName(4), 
				jt.getToken(), s, ANSI_RESET);
	}
	
	/** Writes token and advances to next token
	 */
	private void writeTokenAdv(String s) {
		if (s != null) {
			if (s.equals("-i")) {
				if (! (jt.tokenType() == JackTokenizer.IDENTIFIER)) {
					printErrMsg(s);
					return;
				}
			}
			else if (s.equals("-t")) {
				if (! isaType()) {
					printErrMsg(s);
					return;
				}
			}
			else if (s.equals("-t2")) {
				if (! isaType2()) {
					printErrMsg(s);
					return;
				}	
			}
			else if (! tokenIs(s)) {
				printErrMsg(s);
				return;
			}
		}
		
		writeToken();
		jt.advance();
	}
	
	/** Writes token and advances to next token. Returns token value
	 * @return tokenVal
	 */
	private String writeTokenAdv2(String s) {
		String val = null;
		if (s != null) {
			if (s.equals("-i")) {
				if (! (jt.tokenType() == JackTokenizer.IDENTIFIER)) {
					printErrMsg(s);
					return null;
				}
			}
			else if (s.equals("-t")) {
				if (! isaType()) {
					printErrMsg(s);
					return null;
				}
			}
			else if (s.equals("-t2")) {
				if (! isaType2()) {
					printErrMsg(s);
					return null;
				}	
			}
			else if (! tokenIs(s)) {
				printErrMsg(s);
				return null;
			}
		}
		
		val = getTokenVal();
		writeToken();
		jt.advance();
		return val;
	}
	
	/** Writes the symbol table entry if token is an identifier, then advance token
	 */
	private void writeSymbolEntryAdv() {
		String name = getTokenVal();
		
		if (! (jt.tokenType() == JackTokenizer.IDENTIFIER)) {
			printErrMsg(jt.getToken());
			return;
		}
		
		writeLine("<identifier>");
		indent++;
		
		writeLine("<name> "  + name 							+ " </name>");
		writeLine("<type> "  + st.TypeOf(name) 					+ " </type>");
		writeLine("<kind> "  + st.KindOf(name) 					+ " </kind>");
		writeLine("<index> " + String.valueOf(st.IndexOf(name)) + " </index>");
		
		indent--;
		writeLine("</identifier>");
		
		jt.advance();
	}
	
	/** Writes a line to output file
	 */
	private void writeLine(String s) {
		try {
			fw.write(indents() + s + '\n');
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		if (debug)
			printCurrentTokenVal();
	}
	
	/** Writes the token in xml format to output file
	 */
	private void writeToken() {
		try {
			fw.write(indents() + jt.getToken() + '\n');
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/** Writes the token given in the param to output file
	 *  Used for look ahead cases
	 */
	private void writeToken(String s) {
		try {
			fw.write(indents() + s + '\n');
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
}
