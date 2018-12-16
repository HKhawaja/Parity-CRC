import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

/**
 * 
 */

/**
 * @author HKhawaja
 * @author Scott F. Kaplan (sfkaplan@cs.amherst.edu)
 *
 */
public class ParityDataLinkLayer extends DataLinkLayer {
	
	// ===============================================================
    // The start tag, stop tag, and the escape tag.
    private final byte startTag  = (byte)'{';
    private final byte stopTag   = (byte)'}';
    private final byte escapeTag = (byte)'\\';
    // ===============================================================


	/* Takes an array of bytes, determines the parity bit for the entire array
	 * and appends it to the start of the data, while framing the entire data
	 * 
	 * @param data: the data to frame
	 * @return the framed data
	 * @see DataLinkLayer#createFrame(byte[])
	 */
	@Override
	protected byte[] createFrame(byte[] data) {
		Deque<Byte> frameToSend = new LinkedList<>();
		
		int parityVal = 0;
		//sum number of 1s in data
		for (int i = 0; i<data.length; i++) {
			parityVal += sumOnes(data[i]);
			addData(frameToSend, data[i]);
		}
		
		if (parityVal%2 == 0) {
			frameToSend.addFirst((byte) 0);
		}
		else {
			frameToSend.addFirst((byte) 1);
		}
		frameToSend.addFirst(startTag);
		frameToSend.add(stopTag);
				
		int counter = 0;
		byte[] arrayToSend = new byte[frameToSend.size()];
		for (byte toAdd: frameToSend) {
			arrayToSend[counter] = toAdd;
			counter++;
		}
		return arrayToSend;
	}

	private void addData(Deque<Byte> frameToSend, byte b) {
		if (b == startTag || b == stopTag || b == escapeTag) {
			frameToSend.add(escapeTag);
		}
		frameToSend.add(b);		
	}

	private int sumOnes(byte data) {
//		//counter variable
//		int numOnes = 0;
//		
//		//check if last bit is 1
//		//shift data to right by one bit until data == 0
//		while (data != 0) {
//			if (data <= -1) {
//				break;
//			}
//			if ((1 & data) > 0) {
//				numOnes++;
//			}
//			data >>>= 1;
//		}
//		return numOnes;
		
		if (data >= 0) {
			//counter variable
			int numOnes = 0;
			
			//check if last bit is 1
			//shift data to right by one bit until data == 0
			while (data != 0) {
				if ((1 & data) > 0) {
					numOnes++;
				}
				data = (byte) (data>>>1);
			}
			return numOnes;
		}
		//if negative, make sure byte isn't sign extended
		else {
			int numOnes = 0;
			while (data != 0) {
				if ((1 & data) > 0) {
					numOnes++;
				}
				data = (byte) ((data & 0xff)>>>1);
			}
			return numOnes;
		}
	}

	/* 
	 * @see DataLinkLayer#processFrame()
	 * 
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
		    } else {
			startTagFound = true;
		    }
		}

		// If there is no start tag, then there is no frame.
		if (!startTagFound) {
//			System.out.println("An error has occurred! No start tag was found.");
		    return null;
		}

		// Extract parity Bit
		int parityVal = -1;
		int counter = 0;
		// Try to extract data while waiting for an unescaped stop tag.
		Queue<Byte> extractedBytes = new LinkedList<Byte>();
		boolean       stopTagFound = false;
		while (!stopTagFound && i.hasNext()) {

		    // Grab the next byte.  If it is...
		    //   (a) An escape tag: Skip over it and grab what follows as
		    //                      literal data.
		    //   (b) A stop tag:    Remove all processed bytes from the buffer and
		    //                      end extraction.
		    //   (c) A start tag:   All that precedes is damaged, so remove it
		    //                      from the buffer and restart extraction.
		    //   (d) Otherwise:     Take it as literal data.
		    byte current = i.next();
		    counter ++;
		    if (counter == 1) {
		    	parityVal = current;
		    	continue;
		    }
		    if (current == escapeTag) {
			if (i.hasNext()) {
			    current = i.next();
			    extractedBytes.add(current);
			} else {
			    // An escape was the last byte available, so this is not a
			    // complete frame.
			    return null;
			}
		    } else if (current == stopTag) {
			cleanBufferUpTo(i);
			stopTagFound = true;
		    } else if (current == startTag) {
			cleanBufferUpTo(i);
			extractedBytes = new LinkedList<Byte>();
			counter=0;
			parityVal = -1;
		    } else {
			extractedBytes.add(current);
		    }

		}

		// If there is no stop tag, then the frame is incomplete.
		if (!stopTagFound) {
//			System.out.println("An error has occurred! No stop tag was found.");
		    return null;
		}
		
		//If the parityVals don't match up, there is an issue
		int numOnes = 0;
		for (Byte data: extractedBytes) {
			numOnes+= sumOnes(data);
		}
		numOnes%=2;

		// Convert to the desired byte array.
		if (debug) {
		    System.out.println("DumbDataLinkLayer.processFrame(): Got whole frame!");
		}
		byte[] extractedData = new byte[extractedBytes.size()];
		int                j = 0;
		i = extractedBytes.iterator();
		while (i.hasNext()) {
		    extractedData[j] = i.next();
		    if (debug) {
			System.out.printf("DumbDataLinkLayer.processFrame():\tbyte[%d] = %c\n",
					  j,
					  extractedData[j]);
		    }
		    j += 1;
		}
		
		if(parityVal != 0 && parityVal != 1) {
			System.out.println("An error was detected! The parity bit was corrupted");
			return null;
		}
		
		if (parityVal != numOnes) {
			System.out.println("A parity error was detected!");
			System.out.println("Corrupted data: " + new String(extractedData));
			return null;
		}

		return extractedData;
		
//		int counter = 0;
//		byte[] bytes = new byte[byteBuffer.size()];
//		Stack<Byte> toAdd = new Stack<>();
//		for (byte inBuffer: byteBuffer) {
//			bytes[counter] = inBuffer;
//			counter++;
//		}
//		if (!startTagExists(bytes)) {
//			return null;
//		}
//		if (!stopTagExists(bytes)) {
//			return null;
//		}
//		
//		int lastStartTag = findLastStartTag(bytes);
//		int lastStopTag = findLastStopTag(bytes);
//		if (lastStopTag < lastStartTag) {
//			return null;
//		}
//		int parityVal = (byte) bytes[lastStartTag+1];
//		int numOnes = 0;
//		for (int i = lastStartTag+2; i<=lastStopTag; i++) {
//			numOnes += sumOnes(bytes[i]);
//			if (bytes[i] == escapeTag) {
//				toAdd.push(bytes[i+1]);
//				i++;
//			}
//			else {
//				toAdd.push(bytes[i]);
//			}
//		}
//		
//		numOnes= numOnes%2;
//		if (parityVal != numOnes) {
//			//do sth
//		}
//		
//		byte[] toReturn = new byte[toAdd.size()];
//		for (int i = toReturn.length-1; i>=0; i--) {
//			toReturn[i] = toAdd.pop();
//		}
//		return toReturn;
	}

//	private int findLastStopTag(byte[] bytes) {
//		for (int i = bytes.length-1; i>=0; i--) {
//			if (bytes[i] == stopTag) {
//				return i;
//			}
//		}
//		return 0;
//	}
//
//	private int findLastStartTag(byte[] bytes) {
//		int start = 0;
//		for (int i = 0; i<bytes.length; i++) {
//			if (bytes[i] == startTag) {
//				start = i;
//			}
//		}
//		return start;
//	}
//
//	private boolean stopTagExists(byte[] bytes) {
//		for (int i = 0; i<bytes.length; i++) {
//			if (bytes[i] == stopTag) {
//				return true;
//			}
//		}
//		return false;
//	}
//
//	private boolean startTagExists(byte[] bytes) {
//		for (int i = 0; i<bytes.length; i++) {
//			if (bytes[i] == startTag) {
//				return true;
//			}
//		}
//		return false;
//	}
	
	private void cleanBufferUpTo (Iterator<Byte> end) {
		Iterator<Byte> i = byteBuffer.iterator();
		while (i.hasNext() && i != end) {
		    i.next();
		    i.remove();
		}
	}

}
