/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.formats;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import opennlp.tools.formats.ADParagraphStream.ParagraphParser.Node;
import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;

/**
 * Stream filter which merges text lines into paragraphs, following the Arvores
 * Deitadas syntax.
 * <p>
 * Information about the format:<br>
 * Susana Afonso.
 * "Árvores deitadas: Descrição do formato e das opções de análise na Floresta Sintáctica"
 * .<br>
 * 12 de Fevereiro de 2006. 
 * http://www.linguateca.pt/documentos/Afonso2006ArvoresDeitadas.pdf 
 * <p>
 * <b>Note:</b> Do not use this class, internal use only!
 */
public class ADParagraphStream extends
    FilterObjectStream<String, ADParagraphStream.Paragraph> {

  public static class Paragraph {

    private String text;
    private Node root;

    public String getText() {
      return text;
    }

    public void setText(String text) {
      this.text = text;
    }

    public Node getRoot() {
      return root;
    }

    public void setRoot(Node root) {
      this.root = root;
    }

  }

  /**
   * Parses a sample of AD corpus. A sentence in AD corpus is represented by a
   * Tree. In this class we declare some types to represent that tree.
   */
  public static class ParagraphParser {

    private Pattern rootPattern = Pattern.compile("^[^:=]+:[^(\\s]+$");
    private Pattern nodePattern = Pattern
        .compile("^([=-]*)([^:=]+:[^\\(\\s]+)(\\(([^\\)]+)\\))?\\s*$");
    private Pattern leafPattern = Pattern
        .compile("^([=-]*)([^:=]+:[^\\(\\s]+)\\(([\"'].+[\"'])?\\s*([^\\)]+)?\\)\\s+(.+)");
    private Pattern punctuationPattern = Pattern.compile("^(=*)(\\W+)$");

    /** 
     * Parse the paragraph 
     */
    public Paragraph parse(String paragraphString) {
      BufferedReader reader = new BufferedReader(new StringReader(
          paragraphString));
      Paragraph sentence = new Paragraph();
      Node root = new Node();
      try {
        // first line is <s ...>
        String line = reader.readLine();
        if (line.startsWith("<s")) {
          // should finde the source source
          while (!line.startsWith("SOURCE")) {
            line = reader.readLine();
            if (line == null) {
              return new Paragraph();
            }
          }
        }
        line = reader.readLine();
        // we should have the plain sentence
        // we remove the first token
        int start = line.indexOf(" ");
        sentence.setText(line.substring(start + 1));
        // now we look for the root node
        line = reader.readLine();

        while (!rootPattern.matcher(line).matches()) {
          line = reader.readLine();
          if (line == null) {
            return sentence;
          }
        }
        // got the root. Add it to the stack
        Stack<Node> nodeStack = new Stack<Node>();
        // we get the complete line

        root.setSyntacticTag("ROOT");
        root.setLevel(0);
        nodeStack.add(root);
        // now we have to take care of the lastLevel. Every time it raises, we
        // will add the
        // leaf to the node at the top. If it decreases, we remove the top.
        //line = reader.readLine();
        while (line.length() != 0 && line.startsWith("</s>") == false) {
          TreeElement element = this.getElement(line);

          // remove elements at same level or higher
          while (!nodeStack.isEmpty()
              && element.getLevel() > 0 && element.getLevel() <= nodeStack.peek().getLevel()) {
            nodeStack.pop();
          }
          if( element.isLeaf() ) {
            if (nodeStack.isEmpty()) {
              root.addElement(element);
            } else {
              nodeStack.peek().addElement(element);
            }
          } else {
            if (!nodeStack.isEmpty()) {
              nodeStack.peek().addElement(element);
            }
            nodeStack.push((Node) element);
          }

          line = reader.readLine();
        }

      } catch (Exception e) {
        System.err.println(paragraphString);
        e.printStackTrace();
        return sentence;
      }
      // second line should be SOURCE
      sentence.setRoot(root);
      return sentence;
    }

    /**
     * Parse a tree element from a AD line
     * 
     * @param line
     *          the AD line
     * @return the tree element
     */
    public TreeElement getElement(String line) {
      // try node
      Matcher nodeMatcher = nodePattern.matcher(line);
      if (nodeMatcher.matches()) {
        int level = nodeMatcher.group(1).length();
        String syntacticTag = nodeMatcher.group(2);
        String morphologicalTag = nodeMatcher.group(3);
        Node node = new Node();
        node.setLevel(level);
        node.setSyntacticTag(syntacticTag);
        node.setMorphologicalTag(morphologicalTag);
        return node;
      }

      Matcher leafMatcher = leafPattern.matcher(line);
      if (leafMatcher.matches()) {
        int level = leafMatcher.group(1).length();
        String syntacticTag = leafMatcher.group(2);
        String lemma = leafMatcher.group(3);
        String morphologicalTag = leafMatcher.group(4);
        String lexeme = leafMatcher.group(5);
        Leaf leaf = new Leaf();
        leaf.setLevel(level);
        leaf.setSyntacticTag(syntacticTag);
        leaf.setMorphologicalTag(morphologicalTag);
        leaf.setLexeme(lexeme);
        if (lemma != null) {
          if (lemma.length() > 2) {
            lemma = lemma.substring(1, lemma.length() - 1);
          }
          leaf.setLemma(lemma);
        }

        return leaf;
      }

      Matcher punctuationMatcher = punctuationPattern.matcher(line);
      if (punctuationMatcher.matches()) {
        int level = punctuationMatcher.group(1).length();
        String lexeme = punctuationMatcher.group(2);
        Leaf leaf = new Leaf();
        leaf.setLevel(level);
        leaf.setLexeme(lexeme);
        return leaf;
      }

      System.err.println("Couldn't parse leaf: " + line);
      Leaf leaf = new Leaf();
      leaf.setLevel(0);
      leaf.setSyntacticTag("");
      leaf.setMorphologicalTag("");
      leaf.setLexeme(line);

      return leaf;
    }

    /** Represents a tree element, Node or Leaf */
    public abstract class TreeElement {

      private String syntacticTag;
      private String morphologicalTag;
      private int level;
      
      public boolean isLeaf() {return false;}

      public void setSyntacticTag(String syntacticTag) {
        this.syntacticTag = syntacticTag;
      }

      public String getSyntacticTag() {
        return syntacticTag;
      }

      public void setLevel(int level) {
        this.level = level;
      }

      public int getLevel() {
        return level;
      }

      public void setMorphologicalTag(String morphologicalTag) {
        this.morphologicalTag = morphologicalTag;
      }

      public String getMorphologicalTag() {
        return morphologicalTag;
      }
    }

    /** Represents the AD node */
    public class Node extends TreeElement {
      private List<TreeElement> elems = new ArrayList<TreeElement>();

      public void addElement(TreeElement element) {
        elems.add(element);
      };

      public TreeElement[] getElements() {
        return elems.toArray(new TreeElement[elems.size()]);
      }

      @Override
      public String toString() {
        StringBuffer sb = new StringBuffer();
        // print itself and its children
        for (int i = 0; i < this.getLevel(); i++) {
          sb.append("=");
        }
        sb.append(this.getSyntacticTag());
        if (this.getMorphologicalTag() != null) {
          sb.append(this.getMorphologicalTag());
        }
        sb.append("\n");
        for (TreeElement element : elems) {
          sb.append(element.toString());
        }
        return sb.toString();
      }
    }

    /** Represents the AD leaf */
    public class Leaf extends TreeElement {

      private String word;
      private String lemma;

      public boolean isLeaf() {return true;}
      
      public void setLexeme(String lexeme) {
        this.word = lexeme;
      }

      public String getLexeme() {
        return word;
      }

      @Override
      public String toString() {
        StringBuffer sb = new StringBuffer();
        // print itself and its children
        for (int i = 0; i < this.getLevel(); i++) {
          sb.append("=");
        }
        if (this.getSyntacticTag() != null) {
          sb.append(this.getSyntacticTag() + "(" + this.getMorphologicalTag()
              + ") ");
        }
        sb.append(this.word + "\n");
        return sb.toString();
      }

      public void setLemma(String lemma) {
        this.lemma = lemma;
      }

      public String getLemma() {
        return lemma;
      }
    }

  }
  
  /** 
   * The start paragraph pattern 
   */
  private static final Pattern start = Pattern.compile("<s[^>]*>");

  /** 
   * The end paragraph pattern 
   */
  private static final Pattern end = Pattern.compile("</s>");

  private ParagraphParser parser;

  public ADParagraphStream(ObjectStream<String> lineStream) {
    super(lineStream);
    parser = new ParagraphParser();
  }

  public Paragraph read() throws IOException {

    StringBuilder paragraph = new StringBuilder();
    boolean paragraphStarted = false;

    while (true) {
      String line = samples.read();

      if (line != null) {

        if (start.matcher(line).matches()) {
          paragraphStarted = true;
        }

        if (paragraphStarted) {
          paragraph.append(line).append('\n');
        }

        if (end.matcher(line).matches()) {
          paragraphStarted = false;
        }

        if (!paragraphStarted && paragraph.length() > 0) {
          return parser.parse(paragraph.toString());
        }

      } else {
        // handle end of file
        if (paragraphStarted) {
          if (paragraph.length() > 0) {
            return parser.parse(paragraph.toString());
          }
        } else {
          return null;
        }
      }
    }
  }
}
