import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Harith
 *
 */
public class CRCDataLinkLayer extends DataLinkLayer {

	// ===============================================================
    // The start tag, stop tag, and the escape tag.
    private final int generatorFunction =  128;
    private final int numGeneratorDigits = 8; 
    // =============================================================== 
    
 // The start tag, stop tag, and the escape tag.
    private final byte startTag  = (byte)'{';
    private final byte stopTag   = (byte)'}';
    
	/* (non-Javadoc)
	 * @see DataLinkLayer#createFrame(byte[])
	 */
	@Override
	protected byte[] createFrame(byte[] data) {
					
		//build String representation of entire data
		String[] rep = new String[data.length];
		for (int i = 0; i<data.length; i++) {
			rep[i] = stringify(data[i]);
		}
		
		//take last byte and shift it to the left accordingly
//		int byteToShift = data[data.length -1];
//		byteToShift <<= (numGeneratorDigits-1);

		String zeros = "";
		for (int i = 0; i<numGeneratorDigits-1; i++) {
			zeros += "0";
		}
		
		rep[rep.length-1] = rep[rep.length-1] + zeros;
		
		//build actual binary String
		//stage 1: shift (r-1) bits to left complete!
		StringBuilder stringBuilder = new StringBuilder();
		for (int i = 0; i<rep.length; i++) {
			stringBuilder.append(rep[i]);
		}
//		stringBuilder.append(Integer.toBinaryString(byteToShift));
		String dataString = removeLeftMostZeros(stringBuilder.toString());
		
		//stage 2: divide data by generator and find remainder
		String remainder = findRemainder (dataString, Integer.toBinaryString(generatorFunction));
		remainder = removeLeftMostZeros(remainder);
		
		//stage 3: XOR last (r-1) bits of data string with remainder
		//remainder could be 0; i.e. empty string! in this case, nothing needed to do
		//dataString is our transmission string!
		if (!remainder.equals("")) {
			String dataSubstring = dataString.substring(dataString.length()-remainder.length());
			String toAdd = Integer.toBinaryString((Integer.parseInt(dataSubstring,2) ^ Integer.parseInt(remainder,2)));
//			dataString = dataString.substring(0,remainder.length()+1) + toAdd;
			dataString = dataString.substring(0,dataString.length()-remainder.length()) + toAdd;
		}
		
		int toRetLength = figureOutLength(dataString);
		byte [] toRetBytes = new byte[toRetLength+2];
		int index = toRetBytes.length-2;
		int count = 0;
		StringBuilder byteSoFar = new StringBuilder();
		for (int i = dataString.length()-1; i>=0; i--) {
			byteSoFar.append(dataString.charAt(i));
			count++;
			if (count == 8) {
				count = 0;
				toRetBytes[index] = (byte) Integer.parseInt(byteSoFar.reverse().toString(), 2);
				index--;
				byteSoFar = new StringBuilder();
			}
		}
		//empty StringBuilder if anything is left in there
		toRetBytes[1] = (byte) Integer.parseInt(byteSoFar.reverse().toString(), 2);
		
//		//append start and stop tags the data [] transmitted
//		byte [] tagsAppended = new byte[toRetLength+2];
		toRetBytes[0] = startTag;
		toRetBytes[toRetBytes.length-1] = stopTag;
//		for (int i = 0; i<toRetBytes.length; i++) {
//			tagsAppended[i+1] = toRetBytes[i];
//		}
//		
//		return tagsAppended;
		return toRetBytes;
	}

	private int figureOutLength(String dataString) {
		int remaining = dataString.length()%8;
		if (remaining == 0) {
			return dataString.length()/8;
		}
		else {
			return 1+ dataString.length()/8; 
		}
	}

	private String findRemainder(String dataString, String generatorString) {
		int generatorStringLength = generatorString.length();
		//maintain start and end indexes into dataString
		int start = 0; 
		int end = generatorStringLength - 1;
		int finalEnd = 0;
		
		//remove leading 0s for dataString
		dataString = removeLeftMostZeros(dataString);
		
		//if len(dataString) < len(generatorFunction), then remainder is the dataString
		if (end+1 > dataString.length()-1) {
			return dataString;
		}
		
		//create part of data String under consideration
		String toConsider = dataString.substring(start, end + 1);
		String remainder = "";
		
		while (end <= dataString.length()-1) {
						
			//XOR - convert to ints, perform xor, and convert back to string
			remainder = Integer.toBinaryString((Integer.parseInt(toConsider,2) ^ Integer.parseInt(generatorString,2)));
			
			//remove left-most zeros
			remainder = removeLeftMostZeros(remainder);
			
			//find how far left most 1 is from last digit
			int d = leftMostOneDistance(remainder);
			
			if (d == 0) {
				//only 0s in remainder
			}
			
			int newEnd = end - d + generatorStringLength;
			
			
			if (newEnd >= dataString.length()) {
				finalEnd = end;
			}
			
			//we need to consider d when we append
			if (end+1 < dataString.length() && newEnd+1 <= dataString.length())
				remainder += dataString.substring(end+1, newEnd + 1);
			end = newEnd;
			toConsider = remainder;
			
		}
		
		//if newEnd is out of bounds, just take rest of dataString & append to remainder
		return remainder + dataString.substring(finalEnd+1);
				
	}

	private int leftMostOneDistance(String remainder) {
		//if no zeros, then at distance 0
		if (!remainder.contains("1")) {
			return 0;
		}
		
		//otherwise, count distance from left
		int d = 0;
		for (int i = 0; i<remainder.length(); i++) {
			if (remainder.charAt(i) == '1') {
				d = i;
				break;
			}
		}
		return (remainder.length() - d);
	}

	private static String stringify(byte data) {
//		if (data >= 0) {
			//first check how many 0s to append to left = 8 - currNumOfBits
			int zerosToAppend = 8 - Integer.toBinaryString(data).length();
			StringBuilder stringBuilder = new StringBuilder();
			//then append them
			for (int i = 0; i<zerosToAppend; i++) {
				stringBuilder.append("0");
			}
			//then append the rest of the data at the end
			stringBuilder.append(Integer.toBinaryString(data));
			if (data<0) {
				return stringBuilder.toString().substring(24);
			}
			return stringBuilder.toString();
//		}
//		return null;
	}

	/* (non-Javadoc)
	 * @see DataLinkLayer#processFrame()
	 */
	@Override
	protected byte[] processFrame() {
		
		// Search for a start tag.  Discard anything prior to it.
		boolean        startTagFound = false;
		Iterator<Byte>             i = byteBuffer.iterator();
		while (!startTagFound && i.hasNext()) {
		    byte current = i.next();
		    if (current != startTag) {
		    	i.remove();
		    } 
		    else {
		    	startTagFound = true;
		    }
		}

		// If there is no start tag, then there is no frame.
		if (!startTagFound) {
			//System.out.println("An error has occurred! No start tag was found.");
		    return null;
		}
		
		boolean stopTagFound = false;
		StringBuilder stringBuilder = new StringBuilder();
		while (!stopTagFound && i.hasNext()) {

		    // Grab the next byte.  If it is...
		    //   (b) A stop tag:    Remove all processed bytes from the buffer and
		    //                      end extraction.
		    //   (c) A start tag:   All that precedes is damaged, so remove it
		    //                      from the buffer and restart extraction.
		    //   (d) Otherwise:     Take it as literal data.
		    byte current = i.next();
		    if (current == stopTag) {
		    	cleanBufferUpTo(i);
		    	stopTagFound = true;
		    } else if (current == startTag) {
		    	cleanBufferUpTo(i);
		    	stringBuilder = new StringBuilder();
		    } else {
		    	stringBuilder.append(stringify(current));
		    }

		}

		// If there is no stop tag, then the frame is incomplete.
		if (!stopTagFound) {
//			System.out.println("An error has occurred! No stop tag was found.");
		    return null;
		}
//		
		
		//create String representation of byteBuffer
//		Iterator<Byte> in = byteBuffer.iterator();
//		StringBuilder stringBuilder = new StringBuilder();
//		while (in.hasNext()) {
//			stringBuilder.append(stringify(in.next()));
//		}
		String dataString = stringBuilder.toString();
		dataString = removeLeftMostZeros(dataString);
		
		//first divide dataString by generator to see if remainder is still 0
		String remainder = findRemainder(dataString, Integer.toBinaryString(generatorFunction));
		remainder = removeLeftMostZeros(remainder);
		
		//if remainder is not 0, throw an error!
		if (!remainder.equals("")) {
			System.out.println("An error has occurred! The remainder is not equal to 0");
			return null;
		}
		
		//if it is, shift data right by r-1 bits & you have your datastring
		dataString = dataString.substring(0,dataString.length()-(numGeneratorDigits-1));
		
		
		//next, create byte array for this data String
		int toRetLength = figureOutLength(dataString);
		byte [] toRetBytes = new byte[toRetLength];
		int index = toRetBytes.length-1;
		int count = 0;
		StringBuilder byteSoFar = new StringBuilder();
		for (int i_ = dataString.length()-1; i_>=0; i_--) {
			byteSoFar.append(dataString.charAt(i_));
			count++;
			if (count == 8) {
				count = 0;
				toRetBytes[index] = (byte) Integer.parseInt(byteSoFar.reverse().toString(), 2);
				index--;
				byteSoFar = new StringBuilder();
			}
		}
		//empty StringBuilder if anything is left in there
		toRetBytes[0] = (byte) Integer.parseInt(byteSoFar.reverse().toString(), 2);
//		byteBuffer.clear();
		//clear buffer
//		in = byteBuffer.iterator();
//		while (in.hasNext()) {
//			in.next();
//			in.remove();
//		}
		return toRetBytes;
	}

	private String removeLeftMostZeros(String data) {
		int cutOff = -1;
		//find first 1
		for (int i = 0; i<data.length(); i++) {
			if (data.charAt(i) == '1') {
				cutOff = i;
				break;
			}
		}
		//if 1 found, then return substring
		if (cutOff >= 0) {
			return data.substring(cutOff);
		}
		//if no 1 found, return empty string
		return "";	
	}
	
	private void cleanBufferUpTo (Iterator<Byte> end) {
		Iterator<Byte> i = byteBuffer.iterator();
		while (i.hasNext() && i != end) {
		    i.next();
		    i.remove();
		}
	}

}
