import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

public class SymbolTable {
	private Map<String, ArrayList<String>> classScope, subScope;
	private int staticCnt, fieldCnt, argCnt, localCnt;
	
	/** Creates a new symbol table
	 */
	public SymbolTable() {
		
		staticCnt = 0;
		fieldCnt  = 0;
		classScope 	  = new LinkedHashMap<String, ArrayList<String>>();
		subScope      = null;
	}
	
	/** Starts a new subroutine scope (i.e., resets the subroutine's
	 *  symbol table).
	 */
	public void startSubroutine() {
		
		argCnt 	 = 0;
		localCnt = 0;
		subScope   = new LinkedHashMap<String, ArrayList<String>>();
	}
	
	/** Defines a new identifier of the given name, type, and kind,
	 *  and assigns it a running index. STATIC and FIELD identifiers
	 *  have a class scope, while ARG and VAR identifiers have a subroutine scope.
	 * @param name
	 * @param type
	 * @param kind (STATIC, FIELD, ARG or VAR)
	 */
	public void define(String name, String type, String kind) {
		
		switch (kind.toLowerCase()) {
		case "static" :
			classScope.put(name, new ArrayList<String>(
					Arrays.asList(name, type, kind, String.valueOf(staticCnt++))));
			break;
		
		case "field" :
			classScope.put(name, new ArrayList<String>(
					Arrays.asList(name, type, kind, String.valueOf(fieldCnt++))));
			break;
			
		case "argument" :
			subScope.put(name, new ArrayList<String>(
					Arrays.asList(name, type, kind, String.valueOf(argCnt++))));
			break;
			
		case "var" : case "local" :
			subScope.put(name, new ArrayList<String>(
					Arrays.asList(name, type, "local", String.valueOf(localCnt++))));
			break;
			
		default:
			break;
		}
	}
	
	/** Returns the number of variables of the given kind already
	 *  defined in the current scope.
	 *  @param kind
	 *  @return int
	 */
	public int VarCount(String kind) {
		int count = 0;
		switch (kind.toLowerCase()) {
		case "static" :
			count = staticCnt;
			break;
		
		case "field" :
			count = fieldCnt;
			break;
			
		case "argument" :
			count = argCnt;
			break;
			
		case "local" :
			count = localCnt;
			break;
			
		default:
			break;
		}
		return count;
	}
	
	/** Returns the kind of the named identifier in the current scope.
	 *  If the identifier is unknown in the current scope, returns NONE.
	 *  @return String (STATIC, FIELD, ARG, VAR, NONE)
	 */
	public String KindOf(String name) {
		ArrayList<String> s = getEntry(name);
		
		if (s == null)
			return "NONE";
		return s.get(2);
	}
	
	/** Returns the type of the named identifier in the current scope.
	 * @param name
	 * @return String
	 */
	public String TypeOf(String name) {
		ArrayList<String> s = getEntry(name);
		
		if (s == null)
			return "NONE";
		return s.get(1);
	}
	
	/** Returns the index assigned to the named identifier.
	 * @return -1 if not found. Otherwise a zero-based index.
	*/
	public int IndexOf(String name) {
		ArrayList<String> s = getEntry(name);
		
		if (s == null)
			return -1;
		return Integer.parseInt(s.get(3));
	}
	
	/** Gets the table entry using the key
	 * @param name
	 * @return ArrayList<String>
	 */
	private ArrayList<String> getEntry(String name) {
		ArrayList<String> s = null;
		
		if (subScope != null)
			s = subScope.get(name);
		
		if (s == null)
			s = classScope.get(name);

		return s;
	}
	
	/** Prints table entry
	 */
	public void Show() {
		Iterator<Entry<String, ArrayList<String>>> entries = classScope.entrySet().iterator();
		
		System.out.println("class symbol table:");
		while (entries.hasNext()) {
			Entry<String, ArrayList<String>> thisEntry = (Entry<String, ArrayList<String>>) entries.next();
			Object key = thisEntry.getKey();
			ArrayList<String> value = (ArrayList<String>) thisEntry.getValue();
			
			System.out.format("%15s %15s %15s %15s\n", key, value.get(1), value.get(2), value.get(3));
		}
		
		System.out.println("\nsubroutine symbol table:");
		if (subScope != null) {
			entries = subScope.entrySet().iterator();
			
			while (entries.hasNext()) {
				Entry<String, ArrayList<String>> thisEntry = (Entry<String, ArrayList<String>>) entries.next();
				Object key = thisEntry.getKey();
				ArrayList<String> value = (ArrayList<String>) thisEntry.getValue();
				
				System.out.format("%15s %15s %15s %15s\n", key, value.get(1), value.get(2), value.get(3));
			}
		}
	}

}
