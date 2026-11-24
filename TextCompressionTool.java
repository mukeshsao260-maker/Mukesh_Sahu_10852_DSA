import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class TextCompressionTool {

    // 1. Huffman Tree Node (Implements Comparable for Min-Heap PriorityQueue)
    static class Node implements Comparable<Node> {
        char ch;
        int freq;
        Node left, right;

        Node(char ch, int freq) {
            this.ch = ch;
            this.freq = freq;
        }

        Node(char ch, int freq, Node left, Node right) {
            this.ch = ch;
            this.freq = freq;
            this.left = left;
            this.right = right;
        }

        boolean isLeaf() {
            return left == null && right == null;
        }

        @Override
        public int compareTo(Node other) {
            return Integer.compare(this.freq, other.freq); // Min-Heap condition
        }
    }

    private Node root;
    private Map<Character, String> huffmanCodes = new HashMap<>(); // Hashing: Char -> Binary Code
    private Map<Character, Integer> freqMap = new HashMap<>();     // Hashing: Char -> Frequency

    // Step 1 & 2: Build Frequency Map (Hashing) + Huffman Tree (Greedy + PriorityQueue)
    private void buildHuffmanTree(String text) {
        freqMap.clear();
        huffmanCodes.clear();
        root = null;

        if (text.isEmpty()) return;

        // Hashing: Frequency count
        for (char ch : text.toCharArray()) {
            freqMap.put(ch, freqMap.getOrDefault(ch, 0) + 1);
        }

        // Priority Queue (Min-Heap)
        PriorityQueue<Node> minHeap = new PriorityQueue<>();
        for (Map.Entry<Character, Integer> entry : freqMap.entrySet()) {
            minHeap.add(new Node(entry.getKey(), entry.getValue()));
        }

        // Greedy Approach: Merge two lowest frequency nodes until 1 root remains
        while (minHeap.size() > 1) {
            Node left = minHeap.poll();
            Node right = minHeap.poll();
            Node parent = new Node('\0', left.freq + right.freq, left, right);
            minHeap.add(parent);
        }

        root = minHeap.poll();
        generateCodes(root, "");
    }

    // Recursive traversal to generate prefix-free binary codes
    private void generateCodes(Node node, String code) {
        if (node == null) return;

        if (node.isLeaf()) {
            huffmanCodes.put(node.ch, code.isEmpty() ? "0" : code);
            return;
        }

        generateCodes(node.left, code + "0");
        generateCodes(node.right, code + "1");
    }

    // Compress text file into a packed binary file
    public void compressFile(String inputFile, String outputFile) {
        try {
            String text = new String(Files.readAllBytes(Paths.get(inputFile)));
            if (text.isEmpty()) {
                System.out.println("File khali hai, compress karne ko kuch nahi hai.");
                return;
            }

            buildHuffmanTree(text);

            // Convert text into a bit-string of '0's and '1's
            StringBuilder encodedBits = new StringBuilder();
            for (char ch : text.toCharArray()) {
                encodedBits.append(huffmanCodes.get(ch));
            }

            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(outputFile)))) {
                // Write Header: Number of unique characters
                out.writeInt(freqMap.size());

                // Write Frequency Table (to rebuild tree during decompression)
                for (Map.Entry<Character, Integer> entry : freqMap.entrySet()) {
                    out.writeChar(entry.getKey());
                    out.writeInt(entry.getValue());
                }

                // Write total valid bits count
                long totalBits = encodedBits.length();
                out.writeLong(totalBits);

                // Bit Packing: Pack 8 bits into 1 byte
                int currentByte = 0;
                int bitCount = 0;

                for (int i = 0; i < encodedBits.length(); i++) {
                    currentByte = (currentByte << 1) | (encodedBits.charAt(i) - '0');
                    bitCount++;

                    if (bitCount == 8) {
                        out.writeByte(currentByte);
                        currentByte = 0;
                        bitCount = 0;
                    }
                }

                // Pad remaining bits in the last byte
                if (bitCount > 0) {
                    currentByte <<= (8 - bitCount);
                    out.writeByte(currentByte);
                }
            }

            printStats(inputFile, outputFile);

        } catch (IOException e) {
            System.err.println("Error compressing file: " + e.getMessage());
        }
    }

    // Decompress binary file back to original text
    public void decompressFile(String compressedFile, String restoredFile) {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(compressedFile)));
             BufferedWriter out = new BufferedWriter(new FileWriter(restoredFile))) {

            // Read Header: Unique characters count
            int uniqueChars = in.readInt();
            freqMap.clear();

            for (int i = 0; i < uniqueChars; i++) {
                char ch = in.readChar();
                int freq = in.readInt();
                freqMap.put(ch, freq);
            }

            // Rebuild Huffman Tree
            PriorityQueue<Node> minHeap = new PriorityQueue<>();
            for (Map.Entry<Character, Integer> entry : freqMap.entrySet()) {
                minHeap.add(new Node(entry.getKey(), entry.getValue()));
            }
            while (minHeap.size() > 1) {
                Node left = minHeap.poll();
                Node right = minHeap.poll();
                minHeap.add(new Node('\0', left.freq + right.freq, left, right));
            }
            root = minHeap.poll();

            // Read total valid bits
            long totalBits = in.readLong();
            long bitsRead = 0;
            Node curr = root;

            // Read bytes and traverse Huffman Tree bit-by-bit
            while (bitsRead < totalBits) {
                int byteVal = in.readUnsignedByte();
                for (int i = 7; i >= 0 && bitsRead < totalBits; i--) {
                    int bit = (byteVal >> i) & 1;
                    bitsRead++;

                    // Edge case: file has only 1 unique character
                    if (root.isLeaf()) {
                        out.write(root.ch);
                        continue;
                    }

                    curr = (bit == 0) ? curr.left : curr.right;

                    if (curr.isLeaf()) {
                        out.write(curr.ch);
                        curr = root;
                    }
                }
            }

            System.out.println("\nDecompression successful! File saved as: " + restoredFile);

        } catch (IOException e) {
            System.err.println("Error decompressing file: " + e.getMessage());
        }
    }

    public void printCodes() {
        System.out.println("\n--- Generated Huffman Codes (HashMap) ---");
        for (Map.Entry<Character, String> entry : huffmanCodes.entrySet()) {
            char ch = entry.getKey();
            if (ch == '\n') System.out.println("'\\n' : " + entry.getValue());
            else if (ch == '\r') System.out.println("'\\r' : " + entry.getValue());
            else if (ch == ' ') System.out.println("' '  : " + entry.getValue());
            else System.out.println("'" + ch + "'  : " + entry.getValue());
        }
    }

    private void printStats(String originalFile, String compressedFile) {
        long origSize = new File(originalFile).length();
        long compSize = new File(compressedFile).length();
        double ratio = (1.0 - (double) compSize / origSize) * 100.0;

        System.out.println("\n--- Compression Stats ---");
        System.out.println("Original Size   : " + origSize + " bytes");
        System.out.println("Compressed Size : " + compSize + " bytes");
        System.out.printf("Space Saved     : %.2f%%\n", ratio);
    }

    public static void main(String[] args) {
        TextCompressionTool tool = new TextCompressionTool();
        Scanner scanner = new Scanner(System.in);

        // Testing ke liye automatic sample.txt create kar rahe hain
        try (FileWriter writer = new FileWriter("sample.txt")) {
            writer.write("Data Structures and Algorithms project in Java using Huffman Tree, Greedy Algorithm, Priority Queue, and Hashing. "
                       + "Huffman coding assigns variable-length codes to input characters based on their frequencies!");
        } catch (IOException e) {
            System.out.println("Sample file creation failed.");
        }

        while (true) {
            System.out.println("\n==============================");
            System.out.println("    TEXT COMPRESSION TOOL     ");
            System.out.println("==============================");
            System.out.println("1. Compress a File");
            System.out.println("2. Decompress a File");
            System.out.println("3. Exit");
            System.out.print("Enter choice: ");

            int choice = scanner.nextInt();
            scanner.nextLine(); // Consume newline

            if (choice == 1) {
                System.out.print("Enter input filename (e.g., sample.txt): ");
                String inFile = scanner.nextLine();
                System.out.print("Enter compressed output filename (e.g., compressed.bin): ");
                String outFile = scanner.nextLine();

                tool.compressFile(inFile, outFile);
                tool.printCodes();
            } else if (choice == 2) {
                System.out.print("Enter compressed filename (e.g., compressed.bin): ");
                String inFile = scanner.nextLine();
                System.out.print("Enter output filename for restored text (e.g., restored.txt): ");
                String outFile = scanner.nextLine();

                tool.decompressFile(inFile, outFile);
            } else if (choice == 3) {
                System.out.println("Exiting...");
                break;
            } else {
                System.out.println("Invalid choice! Try again.");
            }
        }
        scanner.close();
    }
}