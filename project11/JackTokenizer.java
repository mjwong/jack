import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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

/** Handles the compiler's input
 *  Ignores white space,
 *  advances the input, one token at a time
 *  gets the value and type of the current token
 */
public class JackTokenizer {
	
	private FileWriter fw;
	private String result;
	private String currentToken;
	private Stream<String> stream;
	private ArrayList<String> tokens;
	private int tokenPos = 0;		// current Token position in output file filenameT.xml
	private boolean debug = false;	// add syntax highlight for debugging set to true.
	
	// token types
	public final static int KEYWORD 	 = 1;
	public final static int SYMBOL 		 = 2;
	public final static int IDENTIFIER 	 = 3;
	public final static int INT_CONST 	 = 4;
	public final static int STRING_CONST = 5;
	
	// keyword types
	public final static int CLASS		= 10;
	public final static int METHOD		= 11;
	public final static int FUNCTION	= 12;
	public final static int CONSTRUCTOR	= 13;
	public final static int INT			= 14;
	public final static int BOOLEAN		= 15;
	public final static int CHAR		= 16;
	public final static int VOID		= 17;
	public final static int VAR			= 18;
	public final static int STATIC		= 19;
	public final static int FIELD		= 20;
	public final static int LET			= 21;
	public final static int DO			= 22;
	public final static int IF			= 23;
	public final static int ELSE		= 24;
	public final static int WHILE		= 25;
	public final static int RETURN		= 26;
	public final static int TRUE		= 27;
	public final static int FALSE		= 28;
	public final static int NULL		= 29;
	public final static int THIS		= 30;

	// list of reserved keywords
	static ArrayList<String> keywords = new ArrayList<String>( Arrays.asList(
			"class", "constructor", "function", "method", "field", "static", 
			"var", "int", "char", "boolean", "void", "true", "false", "null", 
			"this", "let", "do", "if", "else", "while", "return"));
	
	/** Opens the input .jack file and gets ready to tokenize it.
	 */
	public JackTokenizer(String filename) {
		
		tokens = new ArrayList<String>();
		
		openStream(filename);
		
		doStream();
		
		writeFile(filename);
	}
	
	private void doStream() {
		
		result = "";
        
        stream.forEach(s -> {
        	String str = removeComments(s);
        	if (str != "")
        		result += str.replaceAll("\\s\\s+", " ");	// remove additional spaces
        });

        removeMultilineComments();
		
		tokenize(result);
	}
	
	/** opens file as input stream and trim trailing spaces,
	 *  removes empty lines and whole line comments. 
	 * @param filename
	 */
	private void openStream(String filename) {
    	try {
			stream = Files
					.lines(Paths.get(filename))
			        .parallel() // for parallel processing 
			        .map(String::trim) // to trim line  
					.filter(p -> !p.matches("^(\\s*|//.*|\\n)$"));                   
			        
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/** Removes comments after statements from line
	 *  Works only on one line at a time. Do not use it for concatenated code string.
	 * @param c
	 */
    public static String removeComments(String c) {
		// remove comments
    	int index = c.indexOf("//");	// removes any leftover '//' comments
		if (index != -1)
			c = c.substring(0, index).trim();
    	return c;
    }
    
    /** Removes multi-line comments, works with strings.
     *  Before calling this method, collect all lines into a single string as input.
     */
    private void removeMultilineComments() {
    	// "/(?:/.*?$|\\*[*]*[^\\*/]*[*]*\\*/)"
		final String regex = "/\\*(.|[\r\n])*?\\*/";
		final String subst = "";
	
		final Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);
		final Matcher matcher = pattern.matcher(result);
	
		// The substituted value will be contained in the result variable
		result = matcher.replaceAll(subst);
	
		//System.out.println("Substitution result: " + result);
    }
    
    /** Prepares input stream as an ArrayList of tokens
     * @param c
     */
    private void tokenize(String c) {
		String h, type;
		String s1 = "\\{|\\}|\\[|\\]|\\(|\\)|\\.|\\,|\\;|\\+|\\-|\\*|\\/|\\&|\\||\\<|\\>|\\=|\\~";	// symbol
		String s2 = "\"[^\"]*\"|\'\\\\?[^\']?\'";		// single or double quoted string
		String s3 = "\\d+";								// numeric
		String s4 = "\\w+|[a-zA-Z](\\w*|\\_*|\\w*)";	// word or identifier
		Pattern p = Pattern.compile('(' + s1 + '|' + s2 + '|' + s3 + '|' + s4 + ')');
		Matcher m = p.matcher(c);
		
		while (m.find()) {
			h = c.substring(m.start(), m.end());
			
			type = "";
			if (keywords.contains(h))
				type = "keyword";
			else if (h.matches('(' + s1 + ')'))
				type = "symbol";
			else if (h.matches('(' + s2 + ')')) {
				type = "stringConstant";
				h = h.substring(1, h.length()-1);	// removes enclosing "" or ''
			}
			else if (h.matches('(' + s3 + ')'))
				type = "integerConstant";
			else if (h.matches('(' + s4 + ')'))
				type = "identifier";
			
			if (h != null)
				tokens.add("<" + type + "> " + escape(h) + " </" + type + ">");
			
			if (debug)
				syntaxHighlight(h, type);
		}
    }
	
	/** Are there more tokens in the input?
	 * @return boolean
	 */
	public boolean hasMoreTokens() {
		if (tokenPos < tokens.size())
			return true;
		else
			return false;
	}
	
	/** Gets the next token from the input, and makes it the current token.
	 * 	This method should be called only if hasMoreTokens is true.
	 *  @return void
	 */
	public void advance() {
		if (hasMoreTokens())
			currentToken = tokens.get(tokenPos++);
	}
	
	/** Returns the type of the current token, as a constant.
	 *  @return KEYWORD, SYMBOL, IDENTIFIER, INT_CONST, STRING_CONST
	 */
	public int tokenType() {
		String type = getTokenType();
		int c = -1;
		
		switch(type) {
		case "keyword" :
			c = KEYWORD;
			break;
		case "symbol" :
			c = SYMBOL;
			break;
		case "identifier" :
			c = IDENTIFIER;
			break;
		case "integerConstant" :
			c = INT_CONST;
			break;
		case "stringConstant" :
			c = STRING_CONST;
			break;
		default:
			c = 0;
		}
		return c;
	}
	
	/** Returns the keyword which is the current token, as a constant.
	 *  This method should be called only if tokenType is KEYWORD.
	 *  @return CLASS, METHOD, FUNCTION, CONSTRUCTOR, INT, BOOLEAN,
	 *  CHAR, VOID, VAR, STATIC, FIELD, LET, DO, IF, ELSE, WHILE,
	 *  RETURN, TRUE, FALSE, NULL, THIS
	 */
	public int keyWord() {
		String type = getTokenType();
		String val  = getTokenVal();
		int result = -1;
		
		if (type.equals("keyword")) {
			
			switch (val) {
			case "class" :
				result = CLASS;
				break;
			case "method" :
				result = METHOD;
				break;
			case "function" :
				result = FUNCTION;
				break;
			case "constructor" :
				result = CONSTRUCTOR;
				break;
			case "int" :
				result = INT;
				break;
			case "boolean" :
				result = BOOLEAN;
				break;
			case "char" :
				result = CHAR;
				break;
			case "void" :
				result = VOID;
				break;
			case "var" :
				result = VAR;
				break;
			case "static" :
				result = STATIC;
				break;
			case "field" :
				result = FIELD;
				break;
			case "let" :
				result = LET;
				break;
			case "do" :
				result = DO;
				break;
			case "if" :
				result = IF;
				break;
			case "else" :
				result = ELSE;
				break;
			case "while" :
				result = WHILE;
				break;
			case "return" :
				result = RETURN;
				break;
			case "true" :
				result = TRUE;
				break;
			case "false" :
				result = FALSE;
				break;
			case "null" :
				result = NULL;
				break;
			case "this" :
				result = THIS;
				break;
			default :
			}
			return result;
		}
		else
			return -1;
	}
	
	/** Returns the character which is the current token.
	 *  Should be called only if tokenType is SYMBOL.
	 *  @return char
	 */
	public char symbol() {
		String type = getTokenType();
		char   val  = getTokenVal().charAt(0);
		
		if (type.equals("symbol"))
			return val;
		else
			return '\0';
	}
	
	/** Returns the identifier which is the current token.
	 *  Should be called only if tokenType is IDENTIFIER.
	 * @return string
	 */
	public String identifier() {
		String type = getTokenType();
		String val  = getTokenVal();
		
		if (type.equals("identifier"))
			return val;
		else
			return null;
	}
	
	/** Returns the integer value  of the current token.
	 *  Should be called only if tokenType is INT_CONST.
	 * @return int
	 */
	public int intVal() {
		String type = getTokenType();
		int val     = Integer.parseInt(getTokenVal());
		
		if (type.equals("integerConstant"))
			return val;
		else
			return -1;
	}
	
	/** Returns the string value of the current token,
	 *  without the two enclosing double quotes.
	 *  Should be called only if tokenType is STRING_CONST.
	 * @return string
	 */
	public String stringVal() {
		String type = getTokenType();
		String val  = getTokenVal();
		
		if (type.equals("stringConstant"))
			return val;
		else
			return null;
	}
	
	/** Returns the current Token line no.
	*  @return int
	*/
	public int getTokenPos() {
		return tokenPos;
	}
	
	/** Returns the current token in xml format
	 *  @return string
	 */
	public String getToken() {
		return currentToken;
	}
	
	/** Peeks at n-step look ahead without consuming token
	 * @param step
	 * @return String
	 */
	public String peekToken(int step) {
		return tokens.get(tokenPos + step - 1);	// tokenPos already points to next token
	}
	
	/** Returns the token type of current token
	 * @return string
	 */
	private String getTokenType() {
		final Matcher m = Pattern.compile("\\<([^>]*)\\>").matcher(currentToken);
	    if (m.find())
	        return m.group(1); // first expression from round brackets (Testing)
	    else
	    	return null;
	}
	
	/** Returns the value of current token
	 * @return string
	 */
	private String getTokenVal() {
		/*
		Matcher m = Pattern.compile("\\>\\s([^<]*)\\s\\<").matcher(currentToken);
		if (m.find())
			return m.group(1);
		else
			return null;
		*/
		return xmlToString(currentToken).trim();
	}
	
	/** Outputs syntax highlight for debugging
	 * 
	 * @param s
	 * @param t
	 */
	private void syntaxHighlight(String s, String t) {
		final String ANSI_RESET 	= "\u001B[0m";
		//final String ANSI_BLACK 	= "\u001B[30m";
		final String ANSI_RED 		= "\u001B[31m";
		final String ANSI_GREEN 	= "\u001B[32m";
		final String ANSI_YELLOW 	= "\u001B[33m";
		//final String ANSI_BLUE 		= "\u001B[34m";
		final String ANSI_PURPLE 	= "\u001B[35m";
		final String ANSI_CYAN 		= "\u001B[36m";
		final String ANSI_WHITE 	= "\u001B[37m";
		
		String c;
		
		switch (t) {
		case "keyword" :
			if (s.matches("class|char|int|boolean|void"))
				c = ANSI_CYAN;
			else
				c = ANSI_RED;
			break;
		case "symbol" :
			c = ANSI_GREEN;
			break;
		case "integerConstant" :
			c = ANSI_PURPLE;
			break;
		case "stringConstant" :
			c = ANSI_YELLOW;
			break;
		case "identifier" :
			c = ANSI_WHITE;
			break;
		default :
			c = ANSI_WHITE;
		}
		
		if (s.matches("class|constructor|method|function"))
			System.out.println();
		System.out.format("%s%s%s ", c, s, ANSI_RESET);
		if (s.matches("\\{|\\}|\\;"))
			System.out.println();
	}
	
	private String escape(String s) {
	    StringBuilder builder = new StringBuilder();
	    boolean previousWasASpace = false;
	    for( char c : s.toCharArray() ) {
	        if( c == ' ' ) {
	            if( previousWasASpace ) {
	                builder.append("&nbsp;");
	                previousWasASpace = false;
	                continue;
	            }
	            previousWasASpace = true;
	        } else {
	            previousWasASpace = false;
	        }
	        switch(c) {
	            case '<': builder.append("&lt;"); break;
	            case '>': builder.append("&gt;"); break;
	            case '&': builder.append("&amp;"); break;
	            case '"': builder.append("&quot;"); break;
	            case '\n': builder.append("<br>"); break;
	            // We need Tab support here, because we print StackTraces as HTML
	            case '\t': builder.append("&nbsp; &nbsp; &nbsp;"); break;  
	            default:
	                if( c < 128 ) {
	                    builder.append(c);
	                } else {
	                    builder.append("&#").append((int)c).append(";");
	                }    
	        }
	    }
	    return builder.toString();
	}
	
	/** Parses XML string to retrieve the data
	 * @param xmlStr
	 * @return String
	 */
	public String xmlToString(String xmlStr) {
		DocumentBuilder db = null;
		try {
			db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		InputSource is = new InputSource();
		is.setCharacterStream(new StringReader(xmlStr));
		
		Document doc = null;
		try {
			doc = db.parse(is);
		} catch (SAXException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		NodeList node = doc.getChildNodes();
		Element line = (Element) node.item(0);
		return getCharacterDataFromElement(line);
	}
	
	/** Returns data from xml element
	 * 
	 * @param e
	 * @return String
	 */
	private static String getCharacterDataFromElement(Element e) {
		Node child = e.getFirstChild();

		if (child instanceof CharacterData) {
			CharacterData cd = (CharacterData) child;
			return cd.getData();
		}
    	return "";
	}
	
	private void writeFile(String fileName) {
		try
		{
			File fl = new File(fileName.substring(0, fileName.lastIndexOf(".")) + "T.xml");
			fw = new FileWriter(fl);
			Iterator<String> it = tokens.iterator();
			
			fw.write("<tokens>\n");
			
			while (it.hasNext()) {
				fw.write( it.next() );
				if (it.hasNext())
					fw.write('\n');		// do not add a blank line unless there is a token
			}
			
			fw.write("</tokens>");
			fw.close();
		}
		catch (IOException  e) { 
			e.printStackTrace();
		}
    }
}
