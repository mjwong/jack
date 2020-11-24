import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.CharacterData;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/** Must be recursive.
 */
public class CompilationEngine {
	private FileWriter fw = null;
	private JackTokenizer jt;
	private String previousToken 	= null;		// look ahead 1 step.
	private String currentMethod 	= null;
	private int indent 				= 0;		// xml output indentation
	private int openBraces  		= 0;		// count no. of {
	private int closeBraces 		= 0;		// count no. of }
	private boolean hasAheadToken	= false;	// whether look ahead requires an extra token
	private boolean debug			= false;		// turns on console debugging output
	
	private final String ANSI_RESET 	= "\u001B[0m";	// console terminal color
	private final String ANSI_RED 		= "\u001B[31m";
	private final String ANSI_GREEN 	= "\u001B[32m";
	private final String ANSI_YELLOW 	= "\u001B[33m";
	private final String ANSI_BLUE 		= "\u001B[34m";
	private final String ANSI_CYAN 		= "\u001B[36m";
	
	/** Creates a new compilation engine with the given input and output.
	 *  THe next routing called must be compileClass.
	 */
	public CompilationEngine(String path) {
		
		jt = new JackTokenizer(path);
		
		File fl = new File(path.substring(0, path.lastIndexOf(".")) + ".xml");
		
		try {
			fw = new FileWriter(fl);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		compileClass();
		
		try {
			fw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/** Compiles a complete class.
	 *  Syntax: 'class' className '{' classVarDec* subroutineDec* '}'
	 */
	public void compileClass() {
		
		if ( ! jt.hasMoreTokens())
			return;
		
		jt.advance();	// gets the first token 'class'
		
		if ( isKeyword(JackTokenizer.CLASS) ) {
			writeLine("<class>");
			indent++;
			
			writeToken();
			
			jt.advance();	// gets the class identifier
			
			if (! expectIdentifier()) return;
			
			writeToken();
			
			jt.advance();	// gets the opening brace
			
			if (! expectSymbol('{')) return;

			writeToken();	// writes the opening brace
			
			jt.advance();	// gets 'var' or subrountineDec

			// compiles varDec*
			while ( isKeyword(JackTokenizer.STATIC) ||
					isKeyword(JackTokenizer.FIELD) ) {
				compileClassVarDec();
				
				jt.advance();
			}
			
			// compiles subroutineDec*
			while (	isKeyword(JackTokenizer.METHOD)   ||
					isKeyword(JackTokenizer.FUNCTION) ||
					isKeyword(JackTokenizer.CONSTRUCTOR) ) {
				compileSubroutine();
				
				jt.advance();
			}
			
			if (! expectSymbol('}')) return;	// expects closing brace

			writeToken();	// writes the closing brace
			
			indent--;
			writeLine("</class>");
		}
		else
			System.err.println("Class definition error");
	}
	
	/** Compiles a token by calling the corresponding compileXXX method
	 */
	private void compileToken() {
				
		switch (jt.tokenType()) { 	// KEYWORD, SYMBOL, IDENTIFIER, INT_CONST, STRING_CONST
		
		case JackTokenizer.KEYWORD :
			
			switch (jt.keyWord()) {
			
			case JackTokenizer.FIELD : 
			case JackTokenizer.STATIC :
				compileClassVarDec();
				break;
			
			case JackTokenizer.METHOD :
			case JackTokenizer.FUNCTION :
			case JackTokenizer.CONSTRUCTOR :
				compileSubroutine();
				break;
			
			case JackTokenizer.VAR :
				compileVarDec();
				break;

			case JackTokenizer.DO :
			case JackTokenizer.LET :
			case JackTokenizer.WHILE :
			case JackTokenizer.RETURN :
			case JackTokenizer.IF :
				compileStatements();
				break;
			
			default:
				break;
			}
			break;
		
		default:
			break;
		}
	};
	
	/** Compiles a static declaration or a field declaration.
	 *  Process additional tokens until it encounters a symbol ";".
	 *  Syntax: field|static keyword|identifier identifier ;
	 *  Keyword must be either int, boolean or char.
	 *  Syntax: (static|field) type varName (, varName)* ;
	 */
	public void compileClassVarDec() {
		
		writeLine("<classVarDec>");
		indent++;
			
		compileDec();
		
		indent--;
		writeLine("</classVarDec>");
	}
	
	/** Compiles a local or class var declaration statement
	 *  Used by compileClassVarDec and compileVarDec
	 */
	private void compileDec() {
		writeToken();	// writes case (i) keyword 'field' or 'static', case (ii) keyword 'var'
		
		jt.advance(); 	// gets keyword int, boolean, char, or class identifier or built-in types
		
		if (! ( isKeyword(JackTokenizer.BOOLEAN) ||
			    isKeyword(JackTokenizer.CHAR)    ||
			    isKeyword(JackTokenizer.INT)	 ||
			    isType(JackTokenizer.IDENTIFIER) ) ) {
			System.err.println("Expected reserved or built-in types.");
			return;
		}

		writeToken();	// writes types (boolean|char|int) or identifier. Identifier is built-in class type
		
		while (true) {
			
			jt.advance(); 	// gets identifier name
			
			if (! expectIdentifier()) return;
			
			writeToken();	// writes identifier
			
			jt.advance(); 	// gets symbol ',' or ';'
			
			if (! ( isSymbol(',') || isSymbol(';') )) {
				System.err.println("Expected symbol comma or semi-colon.");
				return;
			}
			
			writeToken();	// writes ',' or ';';
			
			if ( isSymbol(';') )
				break;
		}
	}
	
	/** Compiles a complete method, function, or constructor.
	 *  Syntax: (constructor|function|method)  (void|type) subroutineName ( parameterList ) subroutineBody
	 *  e.g. constructor Square new(int x, int y, int size) { statements; }
	 */
	public void compileSubroutine() {
		
		writeLine("<subroutineDec>");
		indent++;
			
		writeToken();	// writes keyword method, function, or constructor
			
		jt.advance(); 	// gets keyword int, boolean, char, void or class identifier or built-in types

		if (! ( isKeyword(JackTokenizer.BOOLEAN) ||
			    isKeyword(JackTokenizer.CHAR)    ||
			    isKeyword(JackTokenizer.INT)	 ||
			    isKeyword(JackTokenizer.VOID)    ||
			    isType(JackTokenizer.IDENTIFIER) ) ) {
			System.err.println("Expected reserved or built-in types.");
			return;
		}
		
		writeToken();	// writes type: keyword or identifier. Identifier is built-in class type
		
		jt.advance(); 	// gets identifier name
		
		if (! expectIdentifier()) return;
		
		writeToken();	// writes identifier
		
		jt.advance(); 	// gets symbol '('
		
		if (! expectSymbol('(')) return;
		
		writeToken();	// writes symbol '('
		
		jt.advance(); 	// gets param or ')'
		
		compileParameterList();
		
		writeToken();	// writes symbol ')'
		
		compileSubroutineBody();
		
		indent--;
		writeLine("</subroutineDec>");
	}
	
	/** Compiles a subroutine body, including { }.
	 *  Syntax: '{' (varDec)* statements '}'
	 *  Keeps track of no. of opening and closing braces.
	 */
	private void compileSubroutineBody() {
		openBraces    = 0;
		closeBraces   = 0;
		
		writeLine("<subroutineBody>");
		indent++;
		
		jt.advance(); 	// gets symbol '{'
		
		if (! expectSymbol('{')) return;
		
		writeToken();	// writes symbol '{'
		
		openBraces++;
		
		jt.advance(); 	// gets 'var' or statements
		
		// compiles varDec*
		while ( isKeyword(JackTokenizer.VAR) ) {
			compileVarDec();
			
			jt.advance();
		}

		compileStatements();	// compiles statements. Gets the next token on return.
		
		printLine(lineNo() + ". Info: In subroutine body after statements. Token is "
				+ getTokenVal(jt.getToken()));
		
		if (! expectSymbol('}')) return;
		
		writeToken();	// writes symbol '}'
		
		closeBraces++;
		
		indent--;
		writeLine("</subroutineBody>");
		
		if (openBraces != closeBraces)
			System.err.println("In Subroutine body : Opening and closing brackets do not match");
	}
	
	/** Compiles a (possibly empty) parameter list, not 
	 *  including the enclosing "()".
	 *  syntax: ( (type varName) ( ',' type varName)* )?
	 *  type: keyword is (char|int|boolean), identifier is built-in class type
	 */
	public void compileParameterList() {
		
		writeLine("<parameterList>");
		indent++;
		
		if (! isSymbol(')')) {
	
			while (true) {	// non-empty param list	
		
				if (! expectKeywordOrIdentifier()) return;
				
				writeToken();	// writes keyword/identifier type
				
				jt.advance(); 	// gets identifier
				
				if (! expectIdentifier()) return;
				
				writeToken();	// writes identifier varName
				
				jt.advance(); 	// gets symbol ',' or ')'
					
				if (! isSymbol(','))
					break;		// should be a ')'
				
				writeToken();	// writes symbol ','
					
				jt.advance();
			}
		}
		
		indent--;
		writeLine("</parameterList>");
	}

	/** Compiles a var declaration.
	 *  Syntax: var type varName (, varName)* ;
	 */
	public void compileVarDec() {
		
		writeLine("<varDec>");
		indent++;
			
		compileDec();
		
		indent--;
		writeLine("</varDec>");
	}
	
	/** Compiles a sequence of statements, not
	 *  including the enclosing "{}".
	 *  Syntax: statement*
	 *  Statement prefix: let | if | while | do | return
	 */
	public void compileStatements() {
		boolean moreStatements = true;
		
		printLine("Entry");
		printCurrentTokenVal();
		
		writeLine("<statements>");
		indent++;
		
		while (moreStatements) {
			
			switch(jt.keyWord()) {
			case JackTokenizer.DO :
				compileDo();
				jt.advance();
				break;
				
			case JackTokenizer.LET :
				compileLet();
				jt.advance();
				break;
				
			case JackTokenizer.WHILE :
				compileWhile();
				jt.advance();
				break;
				
			case JackTokenizer.RETURN :
				compileReturn();
				jt.advance();
				break;
				
			case JackTokenizer.IF :
				compileIf();
				break;
				
			default:
				moreStatements = false;
				break;
			}
			
			printLine("After statement end: More? " + moreStatements);
			printCurrentTokenVal();

			if (! moreStatements)
				break;
		}
		
		printLine("Exit");
		printCurrentTokenVal();
		
		indent--;
		writeLine("</statements>");
	}
	
	/** Compiles a do statement.
	 *  Syntax: 'do' subroutineCall ';'
	 */
	public void compileDo() {
		
		writeLine("<doStatement>");
		indent++;
		
		writeToken();	// writes keyword do
		
		jt.advance();
	
		compileSubroutineCall(null, false);	// false means no look ahead. default.
		
		jt.advance();
		
		if (! expectSymbol(';')) return;
		
		writeToken();	// writes ;
		
		indent--;
		writeLine("</doStatement>");
	}
	
	/** Compiles a subroutine call expression
	 *  Syntax: subroutineName '(' expressionList ')' | 
	 *              (className | varName) '.' subroutineName '(' expressionList ')'
	 */	
	private void compileSubroutineCall(String prevToken, boolean lookAhead) {
		printLine("Entry");
		printCurrentTokenVal();
		
		if (! lookAhead) {
			
			if ( ! expectIdentifier()) return;
			
			writeToken();	// writes subroutineName or (className or varName)
			
			jt.advance();	// advance to either '(' or '.'
		}
		else {
			writeToken(prevToken);
		}

		if (isSymbol('(')) {
			writeToken();	// writes (
			
			jt.advance();
			
			compileExpressionList();
			
			writeToken();	// writes )
		}
		else if (isSymbol('.')) {
			printCurrentTokenVal();
			writeToken();	// writes .

			compileSubroutineWithExpList();
			printCurrentTokenVal();
		}
		else
			printLine(lineNo() + ". In subroutine call : " 
				+ "Expected either opening bracket or dot");
		
		printLine("Exit");
		printCurrentTokenVal();
	}
	
	/** Compiles subroutineName '(' expressionList ')'
	 */
	private void compileSubroutineWithExpList() {
		jt.advance();	// advance to identifier: subroutineName
		
		if ( ! expectIdentifier()) return;
		
		writeToken();	// writes subroutineName
		
		jt.advance();	// advance to '('
		
		if ( ! expectSymbol('(')) return;
		
		writeToken();	// writes '('
		
		jt.advance();
		
		printLine("Before entering expression list");
		printCurrentTokenVal();
		
		compileExpressionList();

		printLine("After exiting expression list");
		printCurrentTokenVal();
		
		//jt.advance();	// gets ')'
		
		writeToken();	// writes )
		
		printLine("Exit");
		printCurrentTokenVal();
	}
	
	/** Compiles a let statement.
	 *  Syntax: 'let' varName ( '[' expression ']' )? '=' expression ';'
	 */
	public void compileLet() {
		
		writeLine("<letStatement>");
		indent++;
		
		writeToken();	// writes keyword let
		
		jt.advance();	// advance to identifier: varName
		
		if ( ! expectIdentifier()) return;
		
		writeToken();	// writes varName
		
		jt.advance();	// advance to symbol '[' or '='

		if ( isSymbol('[')) {
			
			writeToken();	// writes '['
			
			jt.advance();
			
			compileExpression();
			
			if ( ! expectSymbol(']')) return;
			
			writeToken();	// writes ']'
			
			jt.advance();	// gets '='
		}
		
		if ( ! expectSymbol('=')) return; 
			
		writeToken();	// writes '='
		
		jt.advance();
		
		compileExpression();
		
		//jt.advance();
		
		if ( ! expectSymbol(';')) return;
		
		writeToken();	// writes ';'

		indent--;
		writeLine("</letStatement>");
	}
	
	/** Compiles a while statement.
	 *  Syntax: while ( expression ) { statements }
	 */
	public void compileWhile() {
		
		writeLine("<whileStatement>");
		indent++;
		
		compileIfWhileExpStatementPart();
		
		indent--;
		writeLine("</whileStatement>");
	}
	
	/** Compiles the if or while expression part
	 *  Syntax: 'if'|'while' '(' expression ')' '{' statements '}'
	 */
	private void compileIfWhileExpStatementPart() {
		
		printLine("Entry");
		printCurrentTokenVal();
		
		writeToken();	// writes keyword either if or while
		
		jt.advance();	// advance to symbol '('
		
		if ( ! expectSymbol('(')) return;
		
		writeToken();	// writes '('
		
		jt.advance();
		
		compileExpression();
		
		if ( ! expectSymbol(')')) return;
		
		writeToken();	// writes ')'
		
		jt.advance();	// advance to symbol '{'
		
		if ( ! expectSymbol('{')) return;
		
		writeToken();	// writes '{'
		
		openBraces++;
		
		jt.advance();
		
		compileStatements();
		
		writeToken();	// writes '}'
		
		closeBraces++;
		
		printLine("Exit");
		printCurrentTokenVal();
	}
	
	/** Compiles a bracketed expression
	 *  Syntax: '(' expression ')'
	 */
	private void compileBracketExpression() {
		printLine("Entry");
		printCurrentTokenVal();
		
		if ( ! expectSymbol('(')) return;
		
		writeToken();	// writes '('
		
		jt.advance();
		
		compileExpression();
		
		if ( ! expectSymbol(')')) return;
		
		writeToken();	// writes ')'
		
		jt.advance();	// advance to symbol '{' or term
		
		printLine("Exit");
		printCurrentTokenVal();
	}
	
	/** Compiles a return statement.
	 *  Syntax: 'return' expression? ';'
	 *  return statement can occur anywhere, not necessary the last statement in subroutine body
	 */
	public void compileReturn() {
		
		writeLine("<returnStatement>");
		indent++;
		
		printLine("Entry");
		printCurrentTokenVal();
		
		writeToken();	// writes return
		
		jt.advance();	// gets expression or ;
		
		if ( ! isSymbol(';'))
			compileExpression();
		
		if ( ! expectSymbol(';')) return;
		
		writeToken();	// writes ;
		
		printLine("Exit");
		printCurrentTokenVal();
		
		indent--;
		writeLine("</returnStatement>");
	}
	
	/** Compiles a if statement,
	 *  possibly with a trailing else clause.
	 *  Syntax: 'if' '(' expression ')' '{' statements '}' ( 'else' '{' statements '}' )?
	 */
	public void compileIf() {
		
		printLine("Entry");
		printCurrentTokenVal();
		
		writeLine("<ifStatement>");
		indent++;
		
		compileIfWhileExpStatementPart();
		
		printLine("After compileIfWhileExpStatementPart");
		printCurrentTokenVal();
		
		jt.advance(); 	//gets optional 'else' part
		
		if ( (jt.keyWord() == JackTokenizer.ELSE) ) {
			
			writeToken();	// writes keyword else
			
			jt.advance();	// advance to symbol '{'
			
			if ( ! expectSymbol('{')) return;
			
			writeToken();	// writes '{'
			
			openBraces++;
			
			jt.advance();
			
			compileStatements();
			
			if ( ! expectSymbol('}')) return;
			
			writeToken();	// writes '}'
			
			jt.advance();	// maintains a next token as before entering 'else'
			
			closeBraces++;
		}
		
		printLine("Exit");
		printCurrentTokenVal();
		
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
		
		if (! isSymbol(')')) {	// non-empty param list	
			printCurrentTokenVal();
			compileExpression();
			
			//jt.advance(); 	// gets ',' or ')'. Closing bracket means no more expression
			
			while ( isSymbol(',') ) {
				writeToken();	// writes ','
				printLine("more exp list");
				jt.advance();
				compileExpression();
				
				//jt.advance(); 	// gets ',' or ')'
			}
			
			printCurrentTokenVal();
		}
		
		indent--;
		writeLine("</expressionList>");
	}
	
	/** Compiles an expression.
	 *  Syntax: term (op term)*
	 */
	public void compileExpression() {
		
		writeLine("<expression>");
		indent++;
		
		printLine(lineNo() + ". Info: In expression before calling compileTerm. Token is " 
				+ getTokenVal(jt.getToken()));
		
		compileTerm();
		
		jt.advance(); 	// gets op
		printLine("After compileTerm");
		printCurrentTokenVal();
		
		while ( isOp() ) {
			writeToken();	// writes op
			
			jt.advance();
			
			printLine("term op term");
			
			compileTerm();
			
			jt.advance(); 	// gets op
		}
		
		printLine("Exit");
		printCurrentTokenVal();
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
		
		boolean isTerm = true;
		
		writeLine("<term>");
		indent++;
		
		printLine("Entry");
		printCurrentTokenVal();
		
		switch (jt.tokenType()) { 
		
		case JackTokenizer.INT_CONST:
		case JackTokenizer.STRING_CONST:
			printLine("Constant found");
			printCurrentTokenVal();
			writeToken();				// writes verbatim
			break;
			
		case JackTokenizer.KEYWORD:
			switch (jt.keyWord()) {
			
			case JackTokenizer.TRUE:
			case JackTokenizer.FALSE:
			case JackTokenizer.NULL:
			case JackTokenizer.THIS:
				printLine("Keyword constant found");
				printCurrentTokenVal();
				writeToken();			// writes verbatim
				break;
			default:
					break;
			}
			break;
		
		case JackTokenizer.IDENTIFIER:
			printLine("In term. Identifier found. Need to peek ahead");
			compileTermLookAhead();
			break;
			
		case JackTokenizer.SYMBOL:		// unary op
			if (jt.symbol() == '-' || 
			    jt.symbol() == '~') {
				
				printLine("Unary term found.");
				
				writeToken(); 			// writes - or ~
				
				jt.advance();
				
				compileTerm();
			}
			else if (isSymbol('(')) {	// could be the form: '(' expression ')'
				
				printLine("'(' expression ')' found");
				
				writeToken();			// writes (
				
				jt.advance();
				
				compileExpression();
				
				printLine("After expression");
				printCurrentTokenVal();
				
				//jt.advance(); 			// compileExpression() did not advance token
				
				if (! expectSymbol(')')) return;
				
				writeToken();	// writes )
				
				//jt.advance();
			}
			else
				isTerm = false;
			break;
			
		default:
			isTerm = false;
			break;
		}

		printLine("Exit");
		printCurrentTokenVal();
		indent--;
		writeLine("</term>");
	}
	
	/** Compiles term with 1 token look ahead
	 *  VarName:    identifier
	 *  Array:      identifier '[' term ']'
	 *  Subroutine: identifier ( '[' | '.') ...
	 *  Syntax of subroutineCall: subroutineName '(' expressionList ')' | 
	 *              (className | varName) '.' subroutineName '(' expressionList ')'
	 */
	private void compileTermLookAhead() {

		hasAheadToken  = false; 	// requires 2 tokens for look ahead
		
		String prevToken = jt.getToken();		// identifier
		
		//jt.advance(); 							// look ahead one token for "[", "(", or "." 
		
		//printLine("Prev token: " + prevToken + ". Current: " + getTokenVal(jt.getToken()));
		printLine( "Peek ahead: " + getAheadTokenVal() );
		//if ( jt.tokenType() == JackTokenizer.SYMBOL && isSymbol('[') ) {
		if ( getAheadTokenVal().equals("[") ) {
			
			printLine("Array found");
			jt.advance();
			compileArray(prevToken);			// array found
		}
		//else if ( jt.tokenType() == JackTokenizer.SYMBOL && 
		//			(isSymbol('(') || isSymbol('.')) ) {	// subroutine call found
		else if ( getAheadTokenVal().equals("(") ||
			      getAheadTokenVal().equals(".") ) {
			
			printLine("Subroutine call found");
			jt.advance();
			compileSubroutineCall(prevToken, true);
			printCurrentTokenVal();
		}
		else {
			printLine("Simple term found");
			writeToken(prevToken);				// could be end of statement			    
			// do not advance token in calling subroutine cos look ahead token is not consumed
			hasAheadToken = true;
			printCurrentTokenVal();
			// return to calling method expression to write current token
		}
	}
	
	/** Compiles array term
	 *  Syntax: varName '[' expression ']'
	 */
	private void compileArray(String prevToken) {

		writeToken(prevToken);	// writes prev identifier
		
		writeToken();			// writes '['
		
		jt.advance();
		
		/*
		if (jt.tokenType() == JackTokenizer.IDENTIFIER ||
			jt.tokenType() == JackTokenizer.INT_CONST)		// e.g. A[1] or A[x]
			printLine(lineNo() + ". Error: In array. Expected an identifier or int const but got a '" 
					+ getTokenVal(jt.getToken()) + "'");
			
			writeToken();			// writes int const or varName

			jt.advance();
		*/
		
		compileExpression();
		
		printLine("After compileExpression");
		printCurrentTokenVal();
		
		if ( ! isSymbol(']')) return;
		
		writeToken();			// writes ']'
	}
	
	/** Gets current calling class and method's name. For debugging
	 * @return String
	 */
	private static String getCurrentClassAndMethodNames(int t) {
	    final StackTraceElement e = Thread.currentThread().getStackTrace()[t];
	    final String s = e.getClassName();
	    return s.substring(s.lastIndexOf('.') + 1, s.length()) + "." + e.getMethodName();
	}
	
	/** Gets current calling method's name. For debugging
	 * @return String
	 */
	private static String getCurrentMethodNames(int t) {
	    return Thread.currentThread().getStackTrace()[t].getMethodName();
	}
	
	/** Gets line no. in the token list file filenameT.xml
	 * @return int
	 */
	private int lineNo() {
		return (jt.getTokenPos() + 1);
	}
	
	/** Prints msg with indentation. For debugging
	 * @return String
	 */
	private void printLine(String s) {
		if (debug)
			System.out.println(indentD() + s);
	}
	
	/** Prints value of current token. For debugging
	 * @return string
	 */
	private void printCurrentTokenVal() {
		if (debug)
			System.out.format("%s%s%d. Info  %s : %s%s%s\n", 
				indentD(), ANSI_GREEN, lineNo(), 
				getCurrentMethodNames(3), ANSI_YELLOW, getTokenVal(jt.getToken()),
				ANSI_RESET);
	}
	
	/** Error checking: expects keyword or identifier
	 * @return boolean
	 */
	private boolean expectKeywordOrIdentifier() {
		if ( jt.tokenType() == JackTokenizer.KEYWORD ||
			 jt.tokenType() == JackTokenizer.IDENTIFIER)
			return true;
		
		System.out.format("%s%s%d. Error: In %s : Expected keyword or identifier. %s%s\n",
				indentD(), ANSI_CYAN, lineNo(), currentMethod, 
				jt.getToken(), ANSI_RESET);
		return false;
	}
	
	/** Error checking: expects keyword and value
	 * @return boolean
	 */
	private boolean expectKeyword(int kw) {
		if ( jt.tokenType() == JackTokenizer.KEYWORD ||
			 jt.keyWord() == kw)
			return true;
		
		System.out.format("%s%s%d. Error: In %s : Expected keyword const %d. %s%s\n",
				indentD(),ANSI_CYAN, lineNo(), currentMethod, kw, 
				jt.getToken(), ANSI_RESET);
		return false;
	}
	
	/** Error checking: expects identifier
	 * @return boolean
	 */
	private boolean expectIdentifier() {
		if ( jt.tokenType() == JackTokenizer.IDENTIFIER )
			return true;
		
		System.out.format("%s%s%d. Error: In %s : Expected identifier. %s%s\n",
				indentD(), ANSI_CYAN, lineNo(), currentMethod, 
				jt.getToken(), ANSI_RESET);
		return false;
	}
	
	/** Error checking: expects symbol
	 * @param int
	 * @return boolean
	 */
	private boolean expectSymbol(char sym) {
		if ( isSymbol(sym) )
			return true;
		
		System.out.format("%s%s%d. Error in %s : Expected symbol '%s' but got a '%s'%s\n",
				indentD(), ANSI_CYAN, lineNo(), getCurrentMethodNames(3),
				sym, getTokenVal(jt.getToken()), ANSI_RESET);
		return false;
	}
	
	/** Is it the given token type?
	 *  @param int
	 *  @return boolean
	 */
	private boolean isType(int type) {
		if ( jt.tokenType() == type)
			return true;
		
		return false;
	}
	
	/** Is it the given keyword kw?
	 * @param int
	 * @return boolean
	 */
	private boolean isKeyword(int kw) {
		
		if ( jt.tokenType() == JackTokenizer.KEYWORD && jt.keyWord() == kw )
			return true;
		
		return false;
	}
	
	/** Is it a symbol?
	 * @param char
	 * @return boolean
	 */
	private boolean isSymbol(char sym) {
		if ( jt.tokenType() == JackTokenizer.SYMBOL && jt.symbol() == sym )
			return true;
		else
			return false;
	}
	
	/** Returns the value of given token
	 * @return string
	 */
	private String getTokenVal(String xmlStr) {
		return jt.xmlToString(jt.getToken()).trim();
		/*
		Matcher m = Pattern.compile("\\>\\s([^<>]*)\\s\\<").matcher(xmlStr);
		if (m.find())
			return m.group(1);
		else
			return null;
		*/
	}
	
	/** Returns the value of token at one position ahead.
	 * @return string
	 */
	private String getAheadTokenVal() {
		return jt.xmlToString(jt.peekToken(1)).trim();
		/*
		Matcher m = Pattern.compile("\\>\\s([^<>]*)\\s\\<").matcher(jt.peekToken(1));
		if (m.find())
			return m.group(1);
		else
			return null;
		*/
	}
	
	/** Updates no. of opening or closing braces
	 */
	private void updateBraces() {
		if ( isSymbol('{'))
			openBraces++;
		else if ( isSymbol('}'))
			closeBraces++;
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
	
	/** Writes a line to output file
	 */
	private void writeLine(String s) {
		try {
			fw.write(indents() + s + '\n');
		} catch (IOException e) {
			e.printStackTrace();
		}
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
		String str = getTokenVal(s);
		
		try {
			fw.write(indents() + s + '\n');
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
