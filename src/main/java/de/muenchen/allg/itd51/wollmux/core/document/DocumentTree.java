/*-
 * #%L
 * WollMux
 * %%
 * Copyright (C) 2005 - 2020 Landeshauptstadt München
 * %%
 * Licensed under the EUPL, Version 1.1 or – as soon they will be
 * approved by the European Commission - subsequent versions of the
 * EUPL (the "Licence");
 *
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * http://ec.europa.eu/idabc/eupl5
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 * #L%
 */
package de.muenchen.allg.itd51.wollmux.core.document;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.star.awt.XControlModel;
import com.sun.star.container.XNamed;
import com.sun.star.drawing.XControlShape;
import com.sun.star.lang.XServiceInfo;
import com.sun.star.table.XCell;
import com.sun.star.text.XDependentTextField;
import com.sun.star.text.XTextDocument;
import com.sun.star.text.XTextFrame;
import com.sun.star.text.XTextFramesSupplier;
import com.sun.star.text.XTextRange;
import com.sun.star.text.XTextTable;

import de.muenchen.allg.afid.UNO;
import de.muenchen.allg.afid.UnoCollection;
import de.muenchen.allg.afid.UnoDictionary;
import de.muenchen.allg.afid.UnoIterator;
import de.muenchen.allg.document.text.Bookmark;
import de.muenchen.allg.itd51.wollmux.core.document.commands.DocumentCommands;
import de.muenchen.allg.itd51.wollmux.core.document.nodes.CheckboxNode;
import de.muenchen.allg.itd51.wollmux.core.document.nodes.ContainerNode;
import de.muenchen.allg.itd51.wollmux.core.document.nodes.DropdownNode;
import de.muenchen.allg.itd51.wollmux.core.document.nodes.GroupBookmarkNode;
import de.muenchen.allg.itd51.wollmux.core.document.nodes.InputNode;
import de.muenchen.allg.itd51.wollmux.core.document.nodes.InsertionBookmarkNode;
import de.muenchen.allg.itd51.wollmux.core.document.nodes.Node;
import de.muenchen.allg.itd51.wollmux.core.document.nodes.ParagraphNode;
import de.muenchen.allg.itd51.wollmux.core.document.nodes.TextRangeNode;
import de.muenchen.allg.itd51.wollmux.core.parser.ConfigThingy;
import de.muenchen.allg.itd51.wollmux.core.util.L;
import de.muenchen.allg.itd51.wollmux.core.util.Utils;
import de.muenchen.allg.util.UnoProperty;

/**
 * Stellt die interessanten Teile eines Textdokuments als Baum zur Verfügung.
 *
 * @author Matthias Benkmann (D-III-ITD 5.1)
 */
public class DocumentTree
{

  private static final Logger LOGGER = LoggerFactory
      .getLogger(DocumentTree.class);

  /**
   * Pattern zum Erkennen von setGroups-Bookmarks.
   */
  private static final Pattern GROUP_BOOKMARK =
    DocumentCommands.getPatternForCommand("setGroups");

  public static final int TEXFIELD_TYPE_INPUT = 0;
  public static final int TEXFIELD_TYPE_DROPDOWN = 1;

  /**
   * Die Wurzel des Dokumentbaums.
   */
  private Node root;

  /**
   * Erzeugt einen neuen Dokumentbaum für das Dokument doc.
   */
  public DocumentTree(XTextDocument doc)
  {
    List<Node> topLevelNodes = new ArrayList<>();

    /*
     * Zuerst enumerieren wir den Inhalt des Body Texts
     */
    UnoCollection<XTextRange> textRanges = UnoCollection.getCollection(doc.getText(), XTextRange.class);
    if (textRanges == null)
    {
      return;
    }
    List<Node> nodes = new ArrayList<>();
    handleParagraphEnumeration(textRanges, nodes, doc);
    topLevelNodes.add(new ContainerNode(nodes));

    /*
     * Jetzt kommen die Frames dran.
     */
    XTextFramesSupplier supp = UNO.XTextFramesSupplier(doc);
    UnoDictionary<XTextFrame> frames = UnoDictionary.create(supp.getTextFrames(), XTextFrame.class);
    Set<String> names = frames.keySet();
    if (!names.isEmpty())
    {
      nodes = new ArrayList<>(names.size());
      for (String name :  names)
      {
        XTextFrame frame;
        try
        {
          frame = frames.get(name);
        }
        catch (Exception x)
        {
          LOGGER.trace("", x);
          continue;
        }

        UnoCollection<XTextRange> ranges = UnoCollection.getCollection(frame, XTextRange.class);
        List<Node> childNodes = new ArrayList<>();
        handleParagraphEnumeration(ranges, childNodes, doc);

        nodes.add(new ContainerNode(childNodes));
      }

      topLevelNodes.add(new ContainerNode(nodes));
    }

    root = new ContainerNode(topLevelNodes);
  }

  public Node getRoot()
  {
    return root;
  }

  /**
   * Nimmt eine XEnumeration enu von Absätzen und TextTables und fügt für jedes
   * Element von enu zu nodes einen entsprechenden {@link ContainerNode} hinzu.
   *
   * @param doc
   *          das Dokument in dem die Absätze liegen.
   */
  private void handleParagraphEnumeration(UnoCollection<XTextRange> textRanges,
      List<Node> nodes,
      XTextDocument doc)
  {
    for (XTextRange range : textRanges)
    {
      UnoCollection<XTextRange> portions = UnoCollection.getCollection(range, XTextRange.class);
      if (portions != null) // ist wohl ein SwXParagraph
      {
        handleParagraph(portions, nodes, doc);
      }
      else
      // unterstützt nicht XEnumerationAccess, ist wohl SwXTextTable
      {
        XTextTable table = UNO.XTextTable(range);
        if (table != null)
        {
          handleTextTable(table, nodes, doc);
        }
      }
    }
  }

  /**
   * Fügt nodes einen neuen {@link ContainerNode} hinzu, der die Zellen von table
   * enthält.
   *
   * @param doc
   *          das Dokument das die Tabelle enthält.
   */
  private void handleTextTable(XTextTable table, Collection<Node> nodes,
      XTextDocument doc)
  {
    List<Node> cells = new ArrayList<>();
    String[] cellNames = table.getCellNames();
    for (int i = 0; i < cellNames.length; ++i)
    {
      XCell cell = table.getCellByName(cellNames[i]);
      List<Node> cellContents = new ArrayList<>();
      handleParagraphEnumeration(UnoCollection.getCollection(cell, XTextRange.class), cellContents, doc);
      cells.add(new ContainerNode(cellContents));
    }

    nodes.add(new ContainerNode(cells));
  }

  /**
   * Fügt nodes einen neuen {@link ParagraphNode} hinzu, der die Inhalte des Absatzes
   * paragraph.
   *
   * @param doc
   *          das Dokument das den Absatz enthält.
   */
  private void handleParagraph(UnoCollection<XTextRange> paragraph, Collection<Node> nodes,
      XTextDocument doc)
  {
    List<Node> textPortions = new ArrayList<>();

    /*
     * enumeriere alle TextPortions des Paragraphs
     */
    for (XTextRange textPortion : paragraph)
    {
      String textPortionType = (String) Utils.getProperty(textPortion, UnoProperty.TEXT_PROTION_TYPE);
      if ("Bookmark".equals(textPortionType))
      {
        handleBookmark(textPortion, textPortions, doc);
      }
      else if ("TextField".equals(textPortionType))
      {
        handleTextfield(textPortion, textPortions, doc);
      }
      else if ("Frame".equals(textPortionType))
      {
        handleFrame(textPortion, textPortions, doc);
      }
      else if ("Text".equals(textPortionType))
      {
        XTextRange textRange = UNO.XTextRange(textPortion);
        if (textRange != null)
        {
          textPortions.add(new TextRangeNode(textRange));
        }
      }
      else
        // sonstige TextPortion
        continue;
    }

    nodes.add(new ParagraphNode(textPortions));
  }

  private void handleBookmark(Object textPortion, List<Node> textPortions, XTextDocument doc)
  {
    boolean isStart = false;
    boolean isCollapsed = false;
    XNamed bookmark = null;
    try
    {
      isStart =
          ((Boolean) UnoProperty.getProperty(textPortion, UnoProperty.IS_START)).booleanValue();
      isCollapsed =
          ((Boolean) UnoProperty.getProperty(textPortion, UnoProperty.IS_COLLAPSED)).booleanValue();
      if (isCollapsed)
      {
        isStart = true;
      }
      bookmark = UNO.XNamed(UnoProperty.getProperty(textPortion, UnoProperty.BOOKMARK));
    }
    catch (Exception x)
    {
      LOGGER.trace("", x);
      return;
    }
    if (bookmark == null)
    {
      return;
    }

    String name = bookmark.getName();
    Matcher m = DocumentCommands.INSERTION_BOOKMARK.matcher(name);
    if (m.matches())
    {
      try
      {
        ConfigThingy conf = new ConfigThingy("", null, new StringReader(m.group(1)));
        textPortions.add(new InsertionBookmarkNode(new Bookmark(bookmark, doc),
          isStart, conf));
        if (isCollapsed)
        {
          textPortions.add(new InsertionBookmarkNode(new Bookmark(bookmark, doc),
            false, conf));
        }
      }
      catch (Exception x)
      {
        LOGGER.error(L.m("Fehlerhaftes WM()-Bookmark: \"%1\"", name), x);
        return;
      }
      return;
    }

    m = GROUP_BOOKMARK.matcher(name);
    if (m.matches())
    {
      try
      {
        ConfigThingy conf = new ConfigThingy("", null, new StringReader(m.group(1)));
        textPortions.add(new GroupBookmarkNode(new Bookmark(bookmark, doc),
          isStart, conf));
        if (isCollapsed)
        {
          textPortions.add(new GroupBookmarkNode(new Bookmark(bookmark, doc),
            false, conf));
        }
      }
      catch (Exception x)
      {
        LOGGER.error(L.m("Fehlerhaftes WM()-Bookmark: \"%1\"", name), x);
        return;
      }
    }
  }

  private void handleTextfield(Object textPortion, List<Node> textPortions, XTextDocument doc)
  {
    XDependentTextField textField = null;
    int textfieldType = TEXFIELD_TYPE_INPUT;
    try
    {
      textField = UNO.XDependentTextField(UnoProperty.getProperty(textPortion, UnoProperty.TEXT_FIELD));
      XServiceInfo info = UNO.XServiceInfo(textField);
      if (info.supportsService("com.sun.star.text.TextField.DropDown"))
        textfieldType = TEXFIELD_TYPE_DROPDOWN;
      else if (info.supportsService("com.sun.star.text.TextField.Input"))
        textfieldType = TEXFIELD_TYPE_INPUT;
      else
        return; // sonstiges TextField
    }
    catch (Exception x)
    {
      LOGGER.trace("", x);
      return;
    }

    switch (textfieldType)
    {
      case TEXFIELD_TYPE_INPUT:
        textPortions.add(new InputNode(textField, doc));
        break;
      case TEXFIELD_TYPE_DROPDOWN:
        textPortions.add(new DropdownNode(textField, doc));
        break;
    }
  }

  private void handleFrame(Object textPortion, List<Node> textPortions, XTextDocument doc)
  {
    XControlShape shape = null;
    XControlModel model = null;
    try
    {
      UnoIterator<XControlShape> contentIter = UnoIterator.create(UNO.XContentEnumerationAccess(textPortion)
          .createContentEnumeration("com.sun.star.text.TextPortion"),
          XControlShape.class);
      while (contentIter.hasNext())
      {
        XControlShape tempShape = contentIter.next();
        if (tempShape != null)
        {
          XControlModel tempModel = tempShape.getControl();
          XServiceInfo info = UNO.XServiceInfo(tempModel);
          if (info.supportsService("com.sun.star.form.component.CheckBox"))
          {
            shape = tempShape;
            model = tempModel;
          }
        }
      }
    }
    catch (Exception x)
    {
      LOGGER.trace("", x);
      return;
    }

    if (shape != null && model != null)
    {
      textPortions.add(new CheckboxNode(shape, model, doc));
    }
  }

  /**
   * Liefert eine textuelle Baumdarstellung des Baums mit Wurzel root. Jeder Zeile
   * wird childPrefix vorangestellt.
   */
  public static String treeDump(Node root, String childPrefix)
  {
    StringBuilder buf = new StringBuilder();
    buf.append("" + root.toString() + "\n");
    Iterator<Node> iter = root.iterator();
    while (iter.hasNext())
    {
      Node child = iter.next();
      buf.append(childPrefix + "|\n" + childPrefix + "+--");
      char ch = iter.hasNext() ? '|' : ' ';
      buf.append(treeDump(child, childPrefix + ch + "  "));
    }
    return buf.toString();
  }
}
