/*
 * Dateiname: TextDocumentModel.java
 * Projekt  : WollMux
 * Funktion : Repr�sentiert ein aktuell ge�ffnetes TextDokument.
 * 
 * Copyright (c) 2008 Landeshauptstadt M�nchen
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the European Union Public Licence (EUPL),
 * version 1.0.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * European Union Public Licence for more details.
 *
 * You should have received a copy of the European Union Public Licence
 * along with this program. If not, see
 * http://ec.europa.eu/idabc/en/document/7330
 *
 * �nderungshistorie:
 * Datum      | Wer | �nderungsgrund
 * -------------------------------------------------------------------
 * 15.09.2006 | LUT | Erstellung als TextDocumentModel
 * 03.01.2007 | BNK | +collectNonWollMuxFormFields
 * 11.04.2007 | BNK | [R6176]+removeNonWMBookmarks()
 * 08.04.2007 | BNK | [R18119]Druckfunktion inkompatibel mit Versionen <3.11.1
 * -------------------------------------------------------------------
 *
 * @author Christoph Lutz (D-III-ITD 5.1)
 * @version 1.0
 * 
 */
package de.muenchen.allg.itd51.wollmux;

import java.awt.Window;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.star.awt.DeviceInfo;
import com.sun.star.awt.PosSize;
import com.sun.star.awt.XTopWindow;
import com.sun.star.awt.XWindow;
import com.sun.star.beans.XPropertySet;
import com.sun.star.container.NoSuchElementException;
import com.sun.star.container.XEnumeration;
import com.sun.star.container.XEnumerationAccess;
import com.sun.star.container.XNameAccess;
import com.sun.star.container.XNamed;
import com.sun.star.frame.FrameSearchFlag;
import com.sun.star.frame.XController;
import com.sun.star.frame.XFrame;
import com.sun.star.lang.EventObject;
import com.sun.star.lang.IllegalArgumentException;
import com.sun.star.lang.NoSuchMethodException;
import com.sun.star.lang.XComponent;
import com.sun.star.lang.XMultiServiceFactory;
import com.sun.star.text.XBookmarksSupplier;
import com.sun.star.text.XDependentTextField;
import com.sun.star.text.XTextContent;
import com.sun.star.text.XTextCursor;
import com.sun.star.text.XTextDocument;
import com.sun.star.text.XTextField;
import com.sun.star.text.XTextRange;
import com.sun.star.text.XTextViewCursor;
import com.sun.star.text.XTextViewCursorSupplier;
import com.sun.star.uno.AnyConverter;
import com.sun.star.uno.RuntimeException;
import com.sun.star.uno.UnoRuntime;
import com.sun.star.util.CloseVetoException;
import com.sun.star.util.XCloseListener;
import com.sun.star.view.DocumentZoomType;

import de.muenchen.allg.afid.UNO;
import de.muenchen.allg.itd51.parser.ConfigThingy;
import de.muenchen.allg.itd51.parser.NodeNotFoundException;
import de.muenchen.allg.itd51.parser.SyntaxErrorException;
import de.muenchen.allg.itd51.wollmux.DocumentCommand.OptionalHighlightColorProvider;
import de.muenchen.allg.itd51.wollmux.DocumentCommand.SetJumpMark;
import de.muenchen.allg.itd51.wollmux.FormFieldFactory.FormField;
import de.muenchen.allg.itd51.wollmux.dialog.DialogLibrary;
import de.muenchen.allg.itd51.wollmux.dialog.FormController;
import de.muenchen.allg.itd51.wollmux.dialog.mailmerge.MailMergeNew;
import de.muenchen.allg.itd51.wollmux.former.FormularMax4000;
import de.muenchen.allg.itd51.wollmux.former.insertion.InsertionModel4InputUser;
import de.muenchen.allg.itd51.wollmux.func.Function;
import de.muenchen.allg.itd51.wollmux.func.FunctionFactory;
import de.muenchen.allg.itd51.wollmux.func.FunctionLibrary;
import de.muenchen.allg.itd51.wollmux.func.Values.SimpleMap;

/**
 * Diese Klasse repr�sentiert das Modell eines aktuell ge�ffneten TextDokuments und
 * erm�glicht den Zugriff auf alle interessanten Aspekte des TextDokuments.
 * 
 * @author christoph.lutz
 * 
 */
public class TextDocumentModel
{
  /**
   * Enth�lt die Referenz auf das XTextDocument-interface des eigentlichen
   * TextDocument-Services der zugeh�rigen UNO-Komponente.
   */
  public final XTextDocument doc;

  /**
   * Die dataId unter der die WollMux-Formularbeschreibung in {@link #persistentData}
   * gespeichert wird.
   */
  private static final String DATA_ID_FORMULARBESCHREIBUNG =
    "WollMuxFormularbeschreibung";

  /**
   * Die dataId unter der die WollMux-Formularwerte in {@link #persistentData}
   * gespeichert werden.
   */
  private static final String DATA_ID_FORMULARWERTE = "WollMuxFormularwerte";

  /**
   * Die dataId unter der die Metadaten der Seriendruckfunktion in
   * {@link #persistentData} gespeichert werden.
   */
  private static final String DATA_ID_SERIENDRUCK = "WollMuxSeriendruck";

  /**
   * Die dataId unter der der Name der Druckfunktion in {@link #persistentData}
   * gespeichert wird.
   */
  private static final String DATA_ID_PRINTFUNCTION = "PrintFunction";

  /**
   * Die dataId unter der der Name der Druckfunktion in {@link #persistentData}
   * gespeichert wird.
   */
  private static final String DATA_ID_SETTYPE = "SetType";

  /**
   * Pattern zum Erkennen der Bookmarks, die {@link #deForm()} entfernen soll.
   */
  private static final Pattern BOOKMARK_KILL_PATTERN =
    Pattern.compile("(\\A\\s*(WM\\s*\\(.*CMD\\s*'((form)|(setGroups)|(insertFormValue))'.*\\))\\s*\\d*\\z)"
      + "|(\\A\\s*(WM\\s*\\(.*CMD\\s*'(setType)'.*'formDocument'\\))\\s*\\d*\\z)"
      + "|(\\A\\s*(WM\\s*\\(.*'formDocument'.*CMD\\s*'(setType)'.*\\))\\s*\\d*\\z)");

  /**
   * Pattern zum Erkennen von WollMux-Bookmarks.
   */
  private static final Pattern WOLLMUX_BOOKMARK_PATTERN =
    Pattern.compile("(\\A\\s*(WM\\s*\\(.*\\))\\s*\\d*\\z)");

  /**
   * Prefix, mit dem die Namen aller automatisch generierten dokumentlokalen
   * Funktionen beginnen.
   */
  private static final String AUTOFUNCTION_PREFIX = "AUTOFUNCTION_";

  /**
   * Erm�glicht den Zugriff auf eine Collection aller FormField-Objekte in diesem
   * TextDokument �ber den Namen der zugeordneten ID. Die in dieser Map enthaltenen
   * FormFields sind nicht in {@link #idToTextFieldFormFields} enthalten und
   * umgekehrt.
   */
  private HashMap<String, List<FormField>> idToFormFields;

  /**
   * Liefert zu einer ID eine {@link java.util.List} von FormField-Objekten, die alle
   * zu Textfeldern ohne ein umschlie�endes WollMux-Bookmark geh�ren, aber trotzdem
   * vom WollMux interpretiert werden. Die in dieser Map enthaltenen FormFields sind
   * nicht in {@link #idToFormFields} enthalten und umgekehrt.
   */
  private HashMap<String, List<FormField>> idToTextFieldFormFields;

  /**
   * Enth�lt alle Textfelder ohne ein umschlie�endes WollMux-Bookmark, die vom
   * WollMux interpretiert werden sollen, aber TRAFO-Funktionen verwenden, die nur
   * einen feste Werte zur�ckliefern (d.h. keine Parameter erwarten) Die in dieser
   * Map enthaltenen FormFields sind nicht in {@link #idToTextFieldFormFields}
   * enthalten, da sie keine ID besitzen der sie zugeordnet werden k�nnen.
   */
  private List<FormField> staticTextFieldFormFields;

  /**
   * Falls es sich bei dem Dokument um ein Formular handelt, wird das zugeh�rige
   * FormModel hier gespeichert und beim dispose() des TextDocumentModels mit
   * geschlossen.
   */
  private FormModel formModel;

  /**
   * In diesem Feld wird der CloseListener gespeichert, nachdem die Methode
   * registerCloseListener() aufgerufen wurde.
   */
  private XCloseListener closeListener;

  /**
   * Enth�lt die Instanz des aktuell ge�ffneten, zu diesem Dokument geh�renden
   * FormularMax4000.
   */
  private FormularMax4000 currentMax4000;

  /**
   * Enth�lt die Instanz des aktuell ge�ffneten, zu diesem Dokument geh�renden
   * MailMergeNew.
   */
  private MailMergeNew currentMM;

  /**
   * Dieses Feld stellt ein Zwischenspeicher f�r Fragment-Urls dar. Es wird dazu
   * benutzt, im Fall eines openTemplate-Befehls die urls der �bergebenen
   * frag_id-Liste tempor�r zu speichern.
   */
  private String[] fragUrls;

  /**
   * Enth�lt den Typ des Dokuments oder null, falls keiner gesetzt ist.
   */
  private String type = null;

  /**
   * Enth�lt die Namen der aktuell gesetzten Druckfunktionen.
   */
  private HashSet<String> printFunctions;

  /**
   * Enth�lt die Formularbeschreibung des Dokuments oder null, wenn die
   * Formularbeschreibung noch nicht eingelesen wurde.
   */
  private ConfigThingy formularConf;

  /**
   * Enth�lt die aktuellen Werte der Formularfelder als Zuordnung id -> Wert.
   */
  private HashMap<String, String> formFieldValues;

  /**
   * Verantwortlich f�r das Speichern persistenter Daten.
   */
  private PersistentData persistentData;

  /**
   * Enth�lt die Kommandos dieses Dokuments.
   */
  private DocumentCommands documentCommands;

  /**
   * Enth�lt ein Setmit den Namen aller derzeit unsichtbar gestellter
   * Sichtbarkeitsgruppen.
   */
  private HashSet<String> invisibleGroups;

  /**
   * Der Vorschaumodus ist standardm��ig immer gesetzt - ist dieser Modus nicht
   * gesetzt, so werden in den Formularfeldern des Dokuments nur die Feldnamen in
   * spitzen Klammern angezeigt.
   */
  private boolean formFieldPreviewMode;

  /**
   * Kann �ber setPartOfMultiformDocument gesetzt werden und sollte dann true
   * enthalten, wenn das Dokument ein Teil eines Multiformdokuments ist.
   */
  private boolean partOfMultiform;

  /**
   * Enth�lt ein ein Mapping von alten FRAG_IDs fragId auf die jeweils neuen FRAG_IDs
   * newFragId, die �ber im Dokument enthaltene Dokumentkommando WM(CMD
   * 'overrideFrag' FRAG_ID 'fragId' NEW_FRAG_ID 'newFragId') entstanden sind.
   */
  private HashMap /* of String */<String, String> overrideFragMap;

  /**
   * Enth�lt den Kontext f�r die Funktionsbibliotheken und Dialogbibliotheken dieses
   * Dokuments.
   */
  private HashMap<Object, Object> functionContext;

  /**
   * Enth�lt die Dialogbibliothek mit den globalen und dokumentlokalen
   * Dialogfunktionen oder null, wenn die Dialogbibliothek noch nicht ben�tigt wurde.
   */
  private DialogLibrary dialogLib;

  /**
   * Enth�lt die Funktionsbibliothek mit den globalen und dokumentlokalen Funktionen
   * oder null, wenn die Funktionsbilbiothek noch nicht ben�tigt wurde.
   */
  private FunctionLibrary functionLib;

  /**
   * Enth�lt null oder ab dem ersten Aufruf von getMailmergeConf() die Metadaten f�r
   * den Seriendruck in einem ConfigThingy, das derzeit in der Form
   * "Seriendruck(Datenquelle(...))" aufgebaut ist.
   */
  private ConfigThingy mailmergeConf;

  /**
   * Enth�lt den Controller, der an das Dokumentfenster dieses Dokuments angekoppelte
   * Fenster �berwacht und steuert.
   */
  private CoupledWindowController coupledWindowController = null;

  /**
   * Erzeugt ein neues TextDocumentModel zum XTextDocument doc und sollte nie direkt
   * aufgerufen werden, da neue TextDocumentModels �ber das WollMuxSingleton (siehe
   * WollMuxSingleton.getTextDocumentModel()) erzeugt und verwaltet werden.
   * 
   * @param doc
   */
  public TextDocumentModel(XTextDocument doc)
  {
    this.doc = doc;
    this.idToFormFields = new HashMap<String, List<FormField>>();
    this.idToTextFieldFormFields = new HashMap<String, List<FormField>>();
    this.staticTextFieldFormFields = new Vector<FormField>();
    this.fragUrls = new String[] {};
    this.currentMax4000 = null;
    this.closeListener = null;
    this.printFunctions = new HashSet<String>();
    this.formularConf = null;
    this.formFieldValues = new HashMap<String, String>();
    this.invisibleGroups = new HashSet<String>();
    this.overrideFragMap = new HashMap<String, String>();
    this.functionContext = new HashMap<Object, Object>();
    this.formModel = null;
    this.formFieldPreviewMode = true;

    // Kommandobaum erzeugen (modified-Status dabei unber�hrt lassen):
    boolean modified = getDocumentModified();
    this.documentCommands = new DocumentCommands(UNO.XBookmarksSupplier(doc));
    documentCommands.update();
    setDocumentModified(modified);

    registerCloseListener();

    // WollMuxDispatchInterceptor registrieren
    try
    {
      DispatchHandler.registerDocumentDispatchInterceptor(getFrame());
    }
    catch (java.lang.Exception e)
    {
      Logger.error(L.m("Kann DispatchInterceptor nicht registrieren:"), e);
    }

    // Auslesen der Persistenten Daten:
    this.persistentData = new PersistentData(doc);
    this.type = persistentData.getData(DATA_ID_SETTYPE);
    parsePrintFunctions(persistentData.getData(DATA_ID_PRINTFUNCTION));
    parseFormValues(persistentData.getData(DATA_ID_FORMULARWERTE));

    // Sicherstellen, dass die Schaltfl�chen der Symbolleisten aktiviert werden:
    try
    {
      getFrame().contextChanged();
    }
    catch (java.lang.Exception e)
    {}
  }

  /**
   * Liefert den Dokument-Kommandobaum dieses Dokuments.
   * 
   * @return der Dokument-Kommandobaum dieses Dokuments.
   */
  synchronized public DocumentCommands getDocumentCommands()
  {
    return documentCommands;
  }

  /**
   * Erzeugt einen Iterator �ber alle Sichtbarkeitselemente (Dokumentkommandos und
   * Textbereiche mit dem Namenszusatz 'GROUPS ...'), die in diesem Dokument
   * enthalten sind. Der Iterator liefert dabei zuerst alle Textbereiche (mit
   * GROUPS-Erweiterung) und dann alle Dokumentkommandos des Kommandobaumes in der
   * Reihenfolge, die DocumentCommandTree.depthFirstIterator(false) liefert.
   */
  synchronized public Iterator<VisibilityElement> visibleElementsIterator()
  {
    Vector<VisibilityElement> visibleElements = new Vector<VisibilityElement>();
    for (Iterator<VisibilityElement> iter = documentCommands.setGroupsIterator(); iter.hasNext();)
      visibleElements.add(iter.next());
    return visibleElements.iterator();
  }

  /**
   * Diese Methode wertet den im Dokument enthaltenen PrintFunction-Abschnitt aus
   * (siehe storePrintFunctions()).
   * 
   * Anmerkungen:
   * 
   * o Das Einlesen von ARG Argumenten ist noch nicht implementiert
   * 
   * o WollMux-Versionen zwischen 2188 (3.10.1) und 2544 (4.4.0) (beides inklusive)
   * schreiben fehlerhafterweise immer ConfigThingy-Syntax. Aus dem Vorhandensein
   * eines ConfigThingys kann also NICHT darauf geschlossen werden, dass tats�chlich
   * Argumente oder mehr als eine Druckfunktion vorhanden sind.
   * 
   * 
   * @author Christoph Lutz (D-III-ITD-5.1)
   */
  private void parsePrintFunctions(String data)
  {
    if (data == null || data.length() == 0) return;

    final String errmsg =
      L.m("Fehler beim Einlesen des Druckfunktionen-Abschnitts '%1':", data);

    ConfigThingy conf = new ConfigThingy("dummy");
    try
    {
      conf = new ConfigThingy("dummy", data);
    }
    catch (IOException e)
    {
      Logger.error(errmsg, e);
    }
    catch (SyntaxErrorException e)
    {
      try
      {
        // Abw�rtskompatibilit�t mit �lteren PrintFunction-Bl�cken, in denen nur
        // der Funktionsname steht:
        WollMuxSingleton.checkIdentifier(data);
        conf =
          new ConfigThingy("dummy", "WM(Druckfunktionen((FUNCTION '" + data + "')))");
      }
      catch (java.lang.Exception forgetMe)
      {
        // Fehlermeldung des SyntaxFehlers ausgeben
        Logger.error(errmsg, e);
      }
    }

    ConfigThingy functions =
      conf.query("WM").query("Druckfunktionen").queryByChild("FUNCTION");
    for (Iterator<ConfigThingy> iter = functions.iterator(); iter.hasNext();)
    {
      ConfigThingy func = iter.next();
      String name;
      try
      {
        name = func.get("FUNCTION").toString();
      }
      catch (NodeNotFoundException e)
      {
        // kann nicht vorkommen wg. obigem Query
        continue;
      }

      printFunctions.add(name);
    }
  }

  /**
   * Parst den String value als ConfigThingy und �bertr�gt alle in diesem enthaltenen
   * Formular-Abschnitte in die �bergebene Formularbeschreibung formularConf.
   * 
   * @param formDesc
   *          Wurzelknoten WM einer Formularbeschreibung dem die neuen
   *          Formular-Abschnitte hinzugef�gt werden soll.
   * @param value
   *          darf null oder leer sein und wird in diesem Fall ignoriert; value muss
   *          sich fehlerfrei als ConfigThingy parsen lassen, sonst gibt's eine
   *          Fehlermeldung und es wird nichts hinzugef�gt.
   */
  private static void addToFormDescription(ConfigThingy formDesc, String value)
  {
    if (value == null || value.length() == 0) return;

    ConfigThingy conf = new ConfigThingy("");
    try
    {
      conf = new ConfigThingy("", null, new StringReader(value));
    }
    catch (java.lang.Exception e)
    {
      Logger.error(L.m("Die Formularbeschreibung ist fehlerhaft"), e);
      return;
    }

    // enthaltene Formular-Abschnitte �bertragen:
    ConfigThingy formulare = conf.query("Formular");
    for (Iterator<ConfigThingy> iter = formulare.iterator(); iter.hasNext();)
    {
      ConfigThingy formular = iter.next();
      formDesc.addChild(formular);
    }
  }

  /**
   * Wertet werteStr aus (das von der Form "WM(FormularWerte(...))" sein muss und
   * �bertr�gt die gefundenen Werte nach formFieldValues.
   * 
   * @param werteStr
   *          darf null sein (und wird dann ignoriert) aber nicht der leere String.
   */
  private void parseFormValues(String werteStr)
  {
    if (werteStr == null) return;

    // Werte-Abschnitt holen:
    ConfigThingy werte;
    try
    {
      ConfigThingy conf = new ConfigThingy("", null, new StringReader(werteStr));
      werte = conf.get("WM").get("Formularwerte");
    }
    catch (java.lang.Exception e)
    {
      Logger.error(L.m("Formularwerte-Abschnitt ist fehlerhaft"), e);
      return;
    }

    // "Formularwerte"-Abschnitt auswerten.
    Iterator<ConfigThingy> iter = werte.iterator();
    while (iter.hasNext())
    {
      ConfigThingy element = iter.next();
      try
      {
        String id = element.get("ID").toString();
        String value = element.get("VALUE").toString();
        formFieldValues.put(id, value);
      }
      catch (NodeNotFoundException e)
      {
        Logger.error(e);
      }
    }
  }

  /**
   * Wird derzeit vom DocumentCommandInterpreter aufgerufen und gibt dem
   * TextDocumentModel alle FormFields bekannt, die beim Durchlauf des FormScanners
   * gefunden wurden.
   * 
   * @param idToFormFields
   */
  synchronized public void setIDToFormFields(
      HashMap<String, List<FormFieldFactory.FormField>> idToFormFields)
  {
    this.idToFormFields = idToFormFields;
  }

  /**
   * Diese Methode bestimmt die Vorbelegung der Formularfelder des Formulars und
   * liefert eine HashMap zur�ck, die die id eines Formularfeldes auf den bestimmten
   * Wert abbildet. Der Wert ist nur dann klar definiert, wenn alle FormFields zu
   * einer ID unver�ndert geblieben sind, oder wenn nur untransformierte Felder
   * vorhanden sind, die alle den selben Wert enthalten. Gibt es zu einer ID kein
   * FormField-Objekt, so wird der zuletzt abgespeicherte Wert zu dieser ID aus dem
   * FormDescriptor verwendet. Die Methode sollte erst aufgerufen werden, nachdem dem
   * Model mit setIDToFormFields die verf�gbaren Formularfelder bekanntgegeben
   * wurden.
   * 
   * @return eine vollst�ndige Zuordnung von Feld IDs zu den aktuellen Vorbelegungen
   *         im Dokument. TESTED
   */
  synchronized public HashMap<String, String> getIDToPresetValue()
  {
    HashMap<String, String> idToPresetValue = new HashMap<String, String>();

    // durch alle Werte, die im FormDescriptor abgelegt sind gehen, und
    // vergleichen, ob sie mit den Inhalten der Formularfelder im Dokument
    // �bereinstimmen.
    Iterator<String> idIter = formFieldValues.keySet().iterator();
    while (idIter.hasNext())
    {
      String id = idIter.next();
      String value;
      String lastStoredValue = formFieldValues.get(id);

      List<FormField> fields = idToFormFields.get(id);
      if (fields != null && fields.size() > 0)
      {
        boolean allAreUnchanged = true;
        boolean allAreUntransformed = true;
        boolean allUntransformedHaveSameValues = true;

        String refValue = null;

        Iterator<FormField> j = fields.iterator();
        while (j.hasNext())
        {
          FormField field = j.next();
          String thisValue = field.getValue();

          // Wurde der Wert des Feldes gegen�ber dem zusetzt gespeicherten Wert
          // ver�ndert?
          String transformedLastStoredValue =
            getTranformedValue(lastStoredValue, field.getTrafoName(), true);
          if (!thisValue.equals(transformedLastStoredValue))
            allAreUnchanged = false;

          if (field.getTrafoName() != null)
            allAreUntransformed = false;
          else
          {
            // Referenzwert bestimmen
            if (refValue == null) refValue = thisValue;

            if (thisValue == null || !thisValue.equals(refValue))
              allUntransformedHaveSameValues = false;
          }
        }

        // neuen Formularwert bestimmen. Regeln:
        // 1) Wenn sich kein Formularfeld ge�ndert hat, wird der zuletzt
        // gesetzte Formularwert verwendet.
        // 2) Wenn sich mindestens ein Formularfeld geandert hat, jedoch alle
        // untransformiert sind und den selben Wert enhtalten, so wird dieser
        // gleiche Wert als neuer Formularwert �bernommen.
        // 3) in allen anderen F�llen wird FISHY �bergeben.
        if (allAreUnchanged)
          value = lastStoredValue;
        else
        {
          if (allAreUntransformed && allUntransformedHaveSameValues
            && refValue != null)
            value = refValue;
          else
            value = FormController.FISHY;
        }
      }
      else
      {
        // wenn kein Formularfeld vorhanden ist wird der zuletzt gesetzte
        // Formularwert �bernommen.
        value = lastStoredValue;
      }

      // neuen Wert �bernehmen:
      idToPresetValue.put(id, value);
      Logger.debug2("Add IDToPresetValue: ID=\"" + id + "\" --> Wert=\"" + value
        + "\"");

    }
    return idToPresetValue;
  }

  /**
   * Liefert true, wenn das Dokument Serienbrieffelder enth�lt, ansonsten false.
   */
  synchronized public boolean hasMailMergeFields()
  {
    try
    {
      XEnumeration xenu =
        UNO.XTextFieldsSupplier(doc).getTextFields().createEnumeration();
      while (xenu.hasMoreElements())
      {
        try
        {
          XDependentTextField tf = UNO.XDependentTextField(xenu.nextElement());

          // Dieser Code wurde fr�her verwendet um zu erkennen, ob es sich um
          // ein Datenbankfeld handelt. In P1243 ist jedoch ein Crash
          // aufgef�hrt, der reproduzierbar von diesen Zeilen getriggert wurde:
          // if (tf == null) continue;
          // XPropertySet master = tf.getTextFieldMaster();
          // String column = (String) UNO.getProperty(master, "DataColumnName");
          // if (column != null && column.length() > 0) return true;

          // und hier der Workaround: Wenn es sich um ein Datenbankfeld handelt
          // (das den Service c.s.s.t.TextField.Database implementiert), dann
          // ist auch die Property DataBaseFormat definiert. Es reicht also aus
          // zu testen, ob diese Property definiert ist.
          Object o = UNO.getProperty(tf, "DataBaseFormat");
          if (o != null) return true;
        }
        catch (Exception x)
        {
          Logger.error(x);
        }
      }
    }
    catch (Exception x)
    {
      Logger.error(x);
    }
    return false;
  }

  /**
   * Sammelt alle Formularfelder des Dokuments auf, die nicht von WollMux-Kommandos
   * umgeben sind, jedoch trotzdem vom WollMux verstanden und bef�llt werden (derzeit
   * c,s,s,t,textfield,Database-Felder und manche
   * c,s,s,t,textfield,InputUser-Felder).
   * 
   * @author Matthias Benkmann (D-III-ITD 5.1) TESTED
   */
  synchronized public void collectNonWollMuxFormFields()
  {
    idToTextFieldFormFields.clear();
    staticTextFieldFormFields.clear();

    try
    {
      XEnumeration xenu =
        UNO.XTextFieldsSupplier(doc).getTextFields().createEnumeration();
      while (xenu.hasMoreElements())
      {
        try
        {
          XDependentTextField tf = UNO.XDependentTextField(xenu.nextElement());
          if (tf == null) continue;

          if (UNO.supportsService(tf, "com.sun.star.text.TextField.InputUser"))
          {
            String varName = "" + UNO.getProperty(tf, "Content");
            String funcName = getFunctionNameForUserFieldName(varName);
            if (funcName == null) continue;
            XPropertySet master = getUserFieldMaster(varName);
            FormField f = FormFieldFactory.createInputUserFormField(doc, tf, master);
            Function func = getFunctionLibrary().get(funcName);
            if (func == null)
            {
              Logger.error(L.m(
                "Die im Formularfeld verwendete Funktion '%1' ist nicht definiert.",
                funcName));
              continue;
            }
            String[] pars = func.parameters();
            if (pars.length == 0) staticTextFieldFormFields.add(f);
            for (int i = 0; i < pars.length; i++)
            {
              String id = pars[i];
              if (id != null && id.length() > 0)
              {
                if (!idToTextFieldFormFields.containsKey(id))
                  idToTextFieldFormFields.put(id, new Vector<FormField>());

                List<FormField> formFields = idToTextFieldFormFields.get(id);
                formFields.add(f);
              }
            }
          }

          if (UNO.supportsService(tf, "com.sun.star.text.TextField.Database"))
          {
            XPropertySet master = tf.getTextFieldMaster();
            String id = (String) UNO.getProperty(master, "DataColumnName");
            if (id != null && id.length() > 0)
            {
              if (!idToTextFieldFormFields.containsKey(id))
                idToTextFieldFormFields.put(id, new Vector<FormField>());

              List<FormField> formFields = idToTextFieldFormFields.get(id);
              formFields.add(FormFieldFactory.createDatabaseFormField(doc, tf));
            }
          }
        }
        catch (Exception x)
        {
          Logger.error(x);
        }
      }
    }
    catch (Exception x)
    {
      Logger.error(x);
    }
  }

  /**
   * Der DocumentCommandInterpreter liest sich die Liste der setFragUrls()
   * gespeicherten Fragment-URLs hier aus, wenn die Dokumentkommandos insertContent
   * ausgef�hrt werden.
   * 
   * @return
   */
  synchronized public String[] getFragUrls()
  {
    return fragUrls;
  }

  /**
   * �ber diese Methode kann der openDocument-Eventhandler die Liste der mit einem
   * insertContent-Kommando zu �ffnenden frag-urls speichern.
   */
  synchronized public void setFragUrls(String[] fragUrls)
  {
    this.fragUrls = fragUrls;
  }

  /**
   * Registriert das �berschreiben des Textfragments fragId auf den neuen Namen
   * newFragId im TextDocumentModel, wenn das Textfragment fragId nicht bereits
   * �berschrieben wurde.
   * 
   * @param fragId
   *          Die alte FRAG_ID, die durch newFragId �berschrieben werden soll.
   * @param newFragId
   *          die neue FRAG_ID, die die alte FRAG_ID ersetzt.
   * 
   * @author Christoph Lutz (D-III-ITD-5.1)
   * @throws OverrideFragChainException
   *           Wenn eine fragId oder newFragId bereits Ziel/Quelle einer anderen
   *           Ersetzungsregel sind, dann entsteht eine Ersetzungskette, die nicht
   *           zugelassen ist.
   */
  synchronized public void setOverrideFrag(String fragId, String newFragId)
      throws OverrideFragChainException
  {
    if (overrideFragMap.containsKey(newFragId))
      throw new OverrideFragChainException(newFragId);
    if (overrideFragMap.containsValue(fragId))
      throw new OverrideFragChainException(fragId);
    if (!overrideFragMap.containsKey(fragId))
      overrideFragMap.put(fragId, newFragId);
  }

  public static class OverrideFragChainException extends Exception
  {
    private static final long serialVersionUID = 6792199728784265252L;

    private String fragId;

    public OverrideFragChainException(String fragId)
    {
      this.fragId = fragId;
    }

    public String getMessage()
    {
      return L.m(
        "Mit overrideFrag k�nnen keine Ersetzungsketten definiert werden, das Fragment '%1' taucht jedoch bereits in einem anderen overrideFrag-Kommando auf.",
        fragId);
    }

  }

  /**
   * Liefert die neue FragId zur�ck, die anstelle der FRAG_ID fragId verwendet werden
   * soll und durch ein WM(CMD 'overrideFrag'...)-Kommando gesetzt wurde, oder fragId
   * (also sich selbst), wenn keine �berschreibung definiert ist.
   */
  synchronized public String getOverrideFrag(String fragId)
  {
    if (overrideFragMap.containsKey(fragId))
      return overrideFragMap.get(fragId).toString();
    else
      return fragId;
  }

  /**
   * Setzt die Instanz des aktuell ge�ffneten, zu diesem Dokument geh�renden
   * FormularMax4000.
   * 
   * @param max
   */
  synchronized public void setCurrentFormularMax4000(FormularMax4000 max)
  {
    currentMax4000 = max;
  }

  /**
   * Liefert die Instanz des aktuell ge�ffneten, zu diesem Dokument geh�renden
   * FormularMax4000 zur�ck, oder null, falls kein FormularMax gestartet wurde.
   * 
   * @return
   */
  synchronized public FormularMax4000 getCurrentFormularMax4000()
  {
    return currentMax4000;
  }

  /**
   * Setzt die Instanz des aktuell ge�ffneten, zu diesem Dokument geh�renden
   * MailMergeNew.
   * 
   * @param max
   */
  synchronized public void setCurrentMailMergeNew(MailMergeNew max)
  {
    currentMM = max;
  }

  /**
   * Liefert die Instanz des aktuell ge�ffneten, zu diesem Dokument geh�renden
   * MailMergeNew zur�ck, oder null, falls kein FormularMax gestartet wurde.
   * 
   * @return
   */
  synchronized public MailMergeNew getCurrentMailMergeNew()
  {
    return currentMM;
  }

  /**
   * Liefert true, wenn das Dokument eine Vorlage ist oder wie eine Vorlage behandelt
   * werden soll, ansonsten false.
   * 
   * @return true, wenn das Dokument eine Vorlage ist oder wie eine Vorlage behandelt
   *         werden soll, ansonsten false.
   */
  synchronized public boolean isTemplate()
  {
    if (type != null)
    {
      if (type.equalsIgnoreCase("normalTemplate"))
        return true;
      else if (type.equalsIgnoreCase("templateTemplate"))
        return false;
      else if (type.equalsIgnoreCase("formDocument")) return false;
    }

    // Das Dokument ist automatisch eine Vorlage, wenn es keine zugeh�rige URL
    // gibt (dann steht ja in der Fenster�berschrift auch "Unbenannt1" statt
    // einem konkreten Dokumentnamen).
    return !hasURL();
  }

  /**
   * liefert true, wenn das Dokument eine URL besitzt, die die Quelle des Dokuments
   * beschreibt und es sich damit um ein in OOo im "Bearbeiten"-Modus ge�ffnetes
   * Dokument handelt oder false, wenn das Dokument keine URL besitzt und es sich
   * damit um eine Vorlage handelt.
   * 
   * @return liefert true, wenn das Dokument eine URL besitzt, die die Quelle des
   *         Dokuments beschreibt und es sich damit um ein in OOo im
   *         "Bearbeiten"-Modus ge�ffnetes Dokument handelt oder false, wenn das
   *         Dokument keine URL besitzt und es sich damit um eine Vorlage handelt.
   */
  synchronized public boolean hasURL()
  {
    return doc.getURL() != null && !doc.getURL().equals("");
  }

  /**
   * Liefert true, wenn das Dokument vom Typ formDocument ist ansonsten false.
   * ACHTUNG: Ein Dokument k�nnte theoretisch mit einem WM(CMD'setType'
   * TYPE'formDocument') Kommandos als Formulardokument markiert seine, OHNE eine
   * g�ltige Formularbeschreibung zu besitzen. Dies kann mit der Methode
   * hasFormDescriptor() gepr�ft werden.
   * 
   * @return Liefert true, wenn das Dokument vom Typ formDocument ist ansonsten
   *         false.
   */
  synchronized public boolean isFormDocument()
  {
    return (type != null && type.equalsIgnoreCase("formDocument"));
  }

  /**
   * Liefert true, wenn das Dokument ein Teil eines Multiformdokuments ist.
   * 
   * @return Liefert true, wenn das Dokument Teil eines Multiformdokuments ist.
   */
  synchronized public boolean isPartOfMultiformDocument()
  {
    return partOfMultiform;
  }

  synchronized public void setPartOfMultiformDocument(boolean partOfMultiform)
  {
    this.partOfMultiform = partOfMultiform;
  }

  /**
   * Liefert true, wenn das Dokument eine nicht leere Formularbeschreibung mit einem
   * Fenster-Abschnitt enth�lt. In diesem Fall soll das die FormGUI gestartet werden.
   */
  synchronized public boolean hasFormGUIWindow()
  {
    return getFormDescription().query("Formular").query("Fenster").count() != 0;
  }

  /**
   * Setzt den Typ des Dokuments auf type und speichert den Wert persistent im
   * Dokument ab.
   */
  synchronized public void setType(String type)
  {
    this.type = type;

    // Persistente Daten entsprechend anpassen
    if (type != null)
    {
      persistentData.setData(DATA_ID_SETTYPE, type);
    }
    else
    {
      persistentData.removeData(DATA_ID_SETTYPE);
    }
  }

  /**
   * Wird vom {@link DocumentCommandInterpreter} beim Scannen der Dokumentkommandos
   * aufgerufen wenn ein setType-Dokumentkommando bearbeitet werden muss und setzt
   * den Typ des Dokuments NICHT PERSISTENT auf cmd.getType(), wenn nicht bereits ein
   * type gesetzt ist. Ansonsten wird das Kommando ignoriert.
   */
  synchronized public void setType(DocumentCommand.SetType cmd)
  {
    if (type == null) this.type = cmd.getType();
  }

  /**
   * Diese Methode f�gt die Druckfunktion functionName der Menge der dem Dokument
   * zugeordneten Druckfunktionen hinzu. FunctionName muss dabei ein g�ltiger
   * Funktionsbezeichner sein.
   * 
   * @param functionName
   *          der Name der Druckfunktion, der ein g�ltiger Funktionsbezeichner sein
   *          und in einem Abschnitt "Druckfunktionen" in der wollmux.conf definiert
   *          sein muss.
   */
  synchronized public void addPrintFunction(String functionName)
  {
    printFunctions.add(functionName);
    storePrintFunctions();

    // Frame veranlassen, die dispatches neu einzulesen - z.B. damit File->Print
    // auch auf die neue Druckfunktion reagiert.
    try
    {
      getFrame().contextChanged();
    }
    catch (java.lang.Exception e)
    {}
  }

  /**
   * L�scht die Druckfunktion functionName aus der Menge der dem Dokument
   * zugeordneten Druckfunktionen.
   * 
   * Wird z.B. in den Sachleitenden Verf�gungen verwendet, um auf die urspr�nglich
   * gesetzte Druckfunktion zur�ck zu schalten, wenn keine Verf�gungspunkte vorhanden
   * sind.
   * 
   * @param functionName
   *          der Name der Druckfunktion, die aus der Menge gel�scht werden soll.
   */
  synchronized public void removePrintFunction(String functionName)
  {
    printFunctions.remove(functionName);
    storePrintFunctions();

    // Frame veranlassen, die dispatches neu einzulesen - z.B. damit File->Print
    // auch auf gel�schte Druckfunktion reagiert.
    try
    {
      getFrame().contextChanged();
    }
    catch (java.lang.Exception e)
    {}
  }

  /**
   * Schreibt den neuen Zustand der internen HashMap printFunctions in die persistent
   * Data oder l�scht den Datenblock, wenn keine Druckfunktion gesetzt ist. F�r die
   * Druckfunktionen gibt es 2 Syntaxvarianten. Ist nur eine einzige Druckfunktion
   * gesetzt ohne Parameter, so enth�lt der Abschnitt nur den Namen der
   * Druckfunktion. Ist entweder mindestens ein Parameter oder mehrere
   * Druckfunktionen gesetzt, so wird stattdessen ein ConfigThingy geschrieben, mit
   * dem Aufbau
   * 
   * <pre>
   * WM(
   *   Druckfunktionen( 
   *     (FUNCTION 'name' ARG 'arg') 
   *          ...
   *     )
   *   )
   * </pre>. Das Argument ARG ist dabei optional und wird nur gesetzt, wenn ARG
   * nicht leer ist.
   * 
   * Anmerkungen:
   * 
   * o Das Schreiben von ARG Argumenten ist noch nicht implementiert
   * 
   * o WollMux-Versionen zwischen 2188 (3.10.1) und 2544 (4.4.0) (beides inklusive)
   * schreiben fehlerhafterweise immer ConfigThingy-Syntax.
   * 
   * @author Christoph Lutz (D-III-ITD-5.1)
   * @author Matthias Benkmann (D-III-ITD D.10)
   * 
   */
  private void storePrintFunctions()
  {
    if (printFunctions.isEmpty())
    {
      persistentData.removeData(DATA_ID_PRINTFUNCTION);
    }
    else
    // if (printFunctions.size() > 0)
    {
      /*
       * Momentan ist es noch unn�tig umst�ndlich, die Bedingung
       * printFunctions.size() > 1 �ber eine Variable nach unten zu tunneln. Au�erdem
       * ist es derzeit noch so, dass man im Fall printFunctions.size() == 1 erst gar
       * kein ConfigThingy zusammenbauen m�sste. Aber wenn einmal Argumente
       * implementiert sind, dann gibt es f�r deren Vorhandensein vielleicht keinen
       * griffigen Test. In dem Fall ist es am einfachsten unten in der Schleife
       * einfach sobald man auf ein ARG st��t diese Variable hier auf true zu setzen.
       */
      boolean needConfigThingy = (printFunctions.size() > 1);

      // Elemente nach Namen sortieren (definierte Reihenfolge bei der Ausgabe)
      ArrayList<String> names = new ArrayList<String>(printFunctions);
      Collections.sort(names);

      ConfigThingy wm = new ConfigThingy("WM");
      ConfigThingy druckfunktionen = new ConfigThingy("Druckfunktionen");
      wm.addChild(druckfunktionen);
      for (Iterator<String> iter = names.iterator(); iter.hasNext();)
      {
        String name = iter.next();
        ConfigThingy list = new ConfigThingy("");
        ConfigThingy nameConf = new ConfigThingy("FUNCTION");
        nameConf.addChild(new ConfigThingy(name));
        list.addChild(nameConf);
        druckfunktionen.addChild(list);
        // if (Argument vorhanden) needConfigThingy = true;
      }

      if (needConfigThingy)
        persistentData.setData(DATA_ID_PRINTFUNCTION, wm.stringRepresentation());
      else
        persistentData.setData(DATA_ID_PRINTFUNCTION,
          printFunctions.iterator().next().toString());
    }
  }

  /**
   * Liefert eine Menge mit den Namen der aktuell gesetzten Druckfunktionen.
   */
  synchronized public Set<String> getPrintFunctions()
  {
    return printFunctions;
  }

  /**
   * Liefert ein HashSet mit den Namen (Strings) aller als unsichtbar markierten
   * Sichtbarkeitsgruppen.
   */
  synchronized public HashSet<String> getInvisibleGroups()
  {
    return invisibleGroups;
  }

  /**
   * Diese Methode setzt die Eigenschaften "Sichtbar" (visible) und die Anzeige der
   * Hintergrundfarbe (showHighlightColor) f�r alle Druckbl�cke eines bestimmten
   * Blocktyps blockName (z.B. allVersions).
   * 
   * @param blockName
   *          Der Blocktyp dessen Druckbl�cke behandelt werden sollen.
   * @param visible
   *          Der Block wird sichtbar, wenn visible==true und unsichtbar, wenn
   *          visible==false.
   * @param showHighlightColor
   *          gibt an ob die Hintergrundfarbe angezeigt werden soll (gilt nur, wenn
   *          zu einem betroffenen Druckblock auch eine Hintergrundfarbe angegeben
   *          ist).
   * 
   * @author Christoph Lutz (D-III-ITD-5.1)
   */
  synchronized public void setPrintBlocksProps(String blockName, boolean visible,
      boolean showHighlightColor)
  {
    Iterator<DocumentCommand> iter = new HashSet<DocumentCommand>().iterator();
    if (SachleitendeVerfuegung.BLOCKNAME_SLV_ALL_VERSIONS.equals(blockName))
      iter = documentCommands.allVersionsIterator();
    if (SachleitendeVerfuegung.BLOCKNAME_SLV_DRAFT_ONLY.equals(blockName))
      iter = documentCommands.draftOnlyIterator();
    if (SachleitendeVerfuegung.BLOCKNAME_SLV_NOT_IN_ORIGINAL.equals(blockName))
      iter = documentCommands.notInOriginalIterator();
    if (SachleitendeVerfuegung.BLOCKNAME_SLV_ORIGINAL_ONLY.equals(blockName))
      iter = documentCommands.originalOnlyIterator();

    while (iter.hasNext())
    {
      DocumentCommand cmd = iter.next();
      cmd.setVisible(visible);
      String highlightColor =
        ((OptionalHighlightColorProvider) cmd).getHighlightColor();

      if (highlightColor != null)
      {
        if (showHighlightColor)
          try
          {
            Integer bgColor = new Integer(Integer.parseInt(highlightColor, 16));
            UNO.setProperty(cmd.getTextRange(), "CharBackColor", bgColor);
          }
          catch (NumberFormatException e)
          {
            Logger.error(L.m(
              "Fehler in Dokumentkommando '%1': Die Farbe HIGHLIGHT_COLOR mit dem Wert '%2' ist ung�ltig.",
              "" + cmd, highlightColor));
          }
        else
        {
          UNO.setPropertyToDefault(cmd.getTextRange(), "CharBackColor");
        }
      }

    }
  }

  /**
   * Liefert einen Iterator zur�ck, der die Iteration aller
   * DraftOnly-Dokumentkommandos dieses Dokuments erm�glicht.
   * 
   * @return ein Iterator, der die Iteration aller DraftOnly-Dokumentkommandos dieses
   *         Dokuments erm�glicht. Der Iterator kann auch keine Elemente enthalten.
   */
  synchronized public Iterator<DocumentCommand> getDraftOnlyBlocksIterator()
  {
    return documentCommands.draftOnlyIterator();
  }

  /**
   * Liefert einen Iterator zur�ck, der die Iteration aller All-Dokumentkommandos
   * dieses Dokuments erm�glicht.
   * 
   * @return ein Iterator, der die Iteration aller All-Dokumentkommandos dieses
   *         Dokuments erm�glicht. Der Iterator kann auch keine Elemente enthalten.
   */
  synchronized public Iterator<DocumentCommand> getAllVersionsBlocksIterator()
  {
    return documentCommands.allVersionsIterator();
  }

  /**
   * Liefert eine SetJumpMark zur�ck, der das erste setJumpMark-Dokumentkommandos
   * dieses Dokuments enth�lt oder null falls kein solches Dokumentkommando vorhanden
   * ist.
   * 
   * @return Liefert eine SetJumpMark zur�ck, der das erste
   *         setJumpMark-Dokumentkommandos dieses Dokuments enth�lt oder null falls
   *         kein solches Dokumentkommando vorhanden ist.
   */
  synchronized public SetJumpMark getFirstJumpMark()
  {
    return documentCommands.getFirstJumpMark();
  }

  /**
   * Diese Methode liefert die FeldIDs aller im Dokument enthaltenen Felder.
   * 
   * @author Christoph Lutz (D-III-ITD-5.1)
   */
  synchronized public Set<String> getAllFieldIDs()
  {
    HashSet<String> ids = new HashSet<String>();
    ids.addAll(idToFormFields.keySet());
    ids.addAll(idToTextFieldFormFields.keySet());
    return ids;
  }

  /**
   * Liefert den ViewCursor des aktuellen Dokuments oder null, wenn kein Controller
   * (oder auch kein ViewCursor) f�r das Dokument verf�gbar ist.
   * 
   * @return Liefert den ViewCursor des aktuellen Dokuments oder null, wenn kein
   *         Controller (oder auch kein ViewCursor) f�r das Dokument verf�gbar ist.
   */
  synchronized public XTextViewCursor getViewCursor()
  {
    if (UNO.XModel(doc) == null) return null;
    XTextViewCursorSupplier suppl =
      UNO.XTextViewCursorSupplier(UNO.XModel(doc).getCurrentController());
    if (suppl != null) return suppl.getViewCursor();
    return null;
  }

  /**
   * Diese Methode liefert true, wenn der viewCursor im Dokument aktuell nicht
   * kollabiert ist und damit einen markierten Bereich aufspannt, andernfalls false.
   * 
   * @author Christoph Lutz (D-III-ITD-5.1)
   */
  synchronized public boolean hasSelection()
  {
    XTextViewCursor vc = getViewCursor();
    if (vc != null)
    {
      return !vc.isCollapsed();
    }
    return false;
  }

  /**
   * Entfernt alle Bookmarks, die keine WollMux-Bookmarks sind aus dem Dokument doc.
   * 
   * @author Matthias Benkmann (D-III-ITD 5.1)
   */
  synchronized public void removeNonWMBookmarks()
  {
    XBookmarksSupplier bmSupp = UNO.XBookmarksSupplier(doc);
    XNameAccess bookmarks = bmSupp.getBookmarks();
    String[] names = bookmarks.getElementNames();
    for (int i = 0; i < names.length; ++i)
    {
      try
      {
        String bookmark = names[i];
        if (!WOLLMUX_BOOKMARK_PATTERN.matcher(bookmark).matches())
        {
          XTextContent bm = UNO.XTextContent(bookmarks.getByName(bookmark));
          bm.getAnchor().getText().removeTextContent(bm);
        }

      }
      catch (Exception x)
      {
        Logger.error(x);
      }
    }
  }

  /**
   * Entfernt die WollMux-Kommandos "insertFormValue", "setGroups", "setType
   * formDocument" und "form", sowie die WollMux-Formularbeschreibung und Daten aus
   * dem Dokument doc.
   * 
   * @author Matthias Benkmann (D-III-ITD 5.1) TESTED
   */
  synchronized public void deForm()
  {
    XBookmarksSupplier bmSupp = UNO.XBookmarksSupplier(doc);
    XNameAccess bookmarks = bmSupp.getBookmarks();
    String[] names = bookmarks.getElementNames();
    for (int i = 0; i < names.length; ++i)
    {
      try
      {
        String bookmark = names[i];
        if (BOOKMARK_KILL_PATTERN.matcher(bookmark).matches())
        {
          XTextContent bm = UNO.XTextContent(bookmarks.getByName(bookmark));
          bm.getAnchor().getText().removeTextContent(bm);
        }

      }
      catch (Exception x)
      {
        Logger.error(x);
      }
    }

    persistentData.removeData(DATA_ID_FORMULARBESCHREIBUNG);
    persistentData.removeData(DATA_ID_FORMULARWERTE);
  }

  /**
   * F�gt an Stelle der aktuellen Selektion ein Serienbrieffeld ein, das auf die
   * Spalte fieldId zugreift und mit dem Wert "" vorbelegt ist, falls noch kein Wert
   * f�r fieldId gesetzt wurde. Das Serienbrieffeld wird im WollMux registriert und
   * kann damit sofort verwendet werden.
   */
  synchronized public void insertMailMergeFieldAtCursorPosition(String fieldId)
  {
    insertMailMergeField(fieldId, getViewCursor());
  }

  /**
   * F�gt an Stelle range ein Serienbrieffeld ein, das auf die Spalte fieldId
   * zugreift und mit dem Wert "" vorbelegt ist, falls noch kein Wert f�r fieldId
   * gesetzt wurde. Das Serienbrieffeld wird im WollMux registriert und kann damit
   * sofort verwendet werden.
   */
  private void insertMailMergeField(String fieldId, XTextRange range)
  {
    if (fieldId == null || fieldId.length() == 0 || range == null) return;
    try
    {
      // Feld einf�gen
      XMultiServiceFactory factory = UNO.XMultiServiceFactory(doc);
      XDependentTextField field =
        UNO.XDependentTextField(factory.createInstance("com.sun.star.text.TextField.Database"));
      XPropertySet master =
        UNO.XPropertySet(factory.createInstance("com.sun.star.text.FieldMaster.Database"));
      UNO.setProperty(master, "DataBaseName", "DataBase");
      UNO.setProperty(master, "DataTableName", "Table");
      UNO.setProperty(master, "DataColumnName", fieldId);
      if (!formFieldPreviewMode)
        UNO.setProperty(field, "Content", "<" + fieldId + ">");
      field.attachTextFieldMaster(master);

      XTextCursor cursor = range.getText().createTextCursorByRange(range);
      cursor.getText().insertTextContent(cursor, field, true);
      cursor.collapseToEnd();

      // Feldwert mit leerem Inhalt vorbelegen
      if (!formFieldValues.containsKey(fieldId)) setFormFieldValue(fieldId, "");

      // Formularfeld bekanntmachen, damit es vom WollMux verwendet wird.
      if (!idToTextFieldFormFields.containsKey(fieldId))
        idToTextFieldFormFields.put(fieldId, new Vector<FormField>());
      List<FormField> formFields = idToTextFieldFormFields.get(fieldId);
      formFields.add(FormFieldFactory.createDatabaseFormField(doc, field));

      // Ansicht des Formularfeldes aktualisieren:
      updateFormFields(fieldId);
    }
    catch (java.lang.Exception e)
    {
      Logger.error(e);
    }
  }

  /**
   * Liefert die aktuelle Formularbeschreibung des Dokuments; Wurde die
   * Formularbeschreibung bis jetzt noch nicht eingelesen, so wird sie sp�testens
   * jetzt eingelesen.
   * 
   * @author Matthias Benkmann, Christoph Lutz (D-III-ITD 5.1)
   */
  synchronized public ConfigThingy getFormDescription()
  {
    if (formularConf == null)
    {
      Logger.debug(L.m("Einlesen der Formularbeschreibung von %1", this));
      formularConf = new ConfigThingy("WM");
      addToFormDescription(formularConf,
        persistentData.getData(DATA_ID_FORMULARBESCHREIBUNG));
    }

    return formularConf;
  }

  /**
   * Liefert den Seriendruck-Knoten der im Dokument gespeicherten
   * Seriendruck-Metadaten zur�ck. Die Metadaten liegen im Dokument beispielsweise in
   * der Form "WM(Seriendruck(Datenquelle(...)))" vor - diese Methode liefert aber
   * nur der Knoten "Seriendruck" zur�ck. Enth�lt das Dokument keine
   * Seriendruck-Metadaten, so liefert diese Methode einen leeren
   * "Seriendruck"-Knoten zur�ck.
   * 
   * @author Christoph Lutz (D-III-ITD 5.1) TESTED
   */
  synchronized public ConfigThingy getMailmergeConfig()
  {
    if (mailmergeConf == null)
    {
      String data = persistentData.getData(DATA_ID_SERIENDRUCK);
      mailmergeConf = new ConfigThingy("Seriendruck");
      if (data != null)
        try
        {
          mailmergeConf =
            new ConfigThingy("", data).query("WM").query("Seriendruck").getLastChild();
        }
        catch (java.lang.Exception e)
        {
          Logger.error(e);
        }
    }
    return mailmergeConf;
  }

  /**
   * Diese Methode speichert die als Kinder von conf �bergebenen Metadaten f�r den
   * Seriendruck persistent im Dokument oder l�scht die Metadaten aus dem Dokument,
   * wenn conf keine Kinder besitzt. conf kann dabei ein beliebig benannter Konten
   * sein, dessen Kinder m�ssen aber g�ltige Schl�ssel des Abschnitts
   * WM(Seriendruck(...) darstellen. So ist z.B. "Datenquelle" ein g�ltiger
   * Kindknoten von conf.
   * 
   * @param conf
   * 
   * @author Christoph Lutz (D-III-ITD-5.1) TESTED
   */
  synchronized public void setMailmergeConfig(ConfigThingy conf)
  {
    mailmergeConf = new ConfigThingy("Seriendruck");
    for (Iterator<ConfigThingy> iter = conf.iterator(); iter.hasNext();)
    {
      ConfigThingy c = new ConfigThingy(iter.next());
      mailmergeConf.addChild(c);
    }
    ConfigThingy wm = new ConfigThingy("WM");
    wm.addChild(mailmergeConf);
    if (mailmergeConf.count() > 0)
      persistentData.setData(DATA_ID_SERIENDRUCK, wm.stringRepresentation());
    else
      persistentData.removeData(DATA_ID_SERIENDRUCK);
  }

  /**
   * Liefert einen Funktionen-Abschnitt der Formularbeschreibung, in dem die lokalen
   * Auto-Funktionen abgelegt werden k�nnen. Besitzt die Formularbeschreibung keinen
   * Funktionen-Abschnitt, so wird der Funktionen-Abschnitt und ggf. auch ein
   * �bergeordneter Formular-Abschnitt neu erzeugt.
   * 
   * @author Christoph Lutz (D-III-ITD-5.1)
   */
  private ConfigThingy getFunktionenConf()
  {
    ConfigThingy formDesc = getFormDescription();
    try
    {
      return formDesc.query("Formular").query("Funktionen").getLastChild();
    }
    catch (NodeNotFoundException e)
    {
      ConfigThingy funktionen = new ConfigThingy("Funktionen");
      ConfigThingy formular;
      try
      {
        formular = formDesc.query("Formular").getLastChild();
      }
      catch (NodeNotFoundException e1)
      {
        formular = new ConfigThingy("Formular");
        formDesc.addChild(formular);
      }
      formular.addChild(funktionen);
      return funktionen;
    }
  }

  /**
   * Speichert die aktuelle Formularbeschreibung in den persistenten Daten des
   * Dokuments oder l�scht den entsprechenden Abschnitt aus den persistenten Daten,
   * wenn die Formularbeschreibung nur aus einer leeren Struktur ohne eigentlichen
   * Formularinhalt besteht.
   * 
   * @author Christoph Lutz (D-III-ITD-5.1)
   */
  private void storeCurrentFormDescription()
  {
    ConfigThingy conf = getFormDescription();
    try
    {
      if ((conf.query("Fenster").count() > 0 && conf.get("Fenster").count() > 0)
        || (conf.query("Sichtbarkeit").count() > 0 && conf.get("Sichtbarkeit").count() > 0)
        || (conf.query("Funktionen").count() > 0 && conf.get("Funktionen").count() > 0))
        persistentData.setData(DATA_ID_FORMULARBESCHREIBUNG,
          conf.stringRepresentation());
      else
        persistentData.removeData(DATA_ID_FORMULARBESCHREIBUNG);
    }
    catch (NodeNotFoundException e)
    {
      Logger.error(L.m("Dies kann nicht passieren."), e);
    }
  }

  /**
   * Ersetzt die Formularbeschreibung dieses Dokuments durch die aus conf. Falls conf ==
   * null, so wird die Formularbeschreibung gel�scht. ACHTUNG! conf wird nicht
   * kopiert sondern als Referenz eingebunden.
   * 
   * @param conf
   *          ein WM-Knoten, der "Formular"-Kinder hat. Falls conf == null, so wird
   *          die Formularbeschreibungsnotiz gel�scht.
   * @author Matthias Benkmann (D-III-ITD 5.1)
   */
  synchronized public void setFormDescription(ConfigThingy conf)
  {
    if (conf != null)
      formularConf = conf;
    else
      formularConf = new ConfigThingy("WM");
    storeCurrentFormDescription();
    setDocumentModified(true);
  }

  /**
   * Speichert den neuen Wert value zum Formularfeld fieldId im
   * Formularwerte-Abschnitt in den persistenten Daten oder l�scht den Eintrag f�r
   * fieldId aus den persistenten Daten, wenn value==null ist. ACHTUNG! Damit der
   * neue Wert angezeigt wird, ist ein Aufruf von {@link #updateFormFields(String)}
   * erforderlich.
   * 
   * @author Matthias Benkmann, Christoph Lutz (D-III-ITD 5.1)
   */
  synchronized public void setFormFieldValue(String fieldId, String value)
  {
    if (value == null)
      formFieldValues.remove(fieldId);
    else
      formFieldValues.put(fieldId, value);
    persistentData.setData(DATA_ID_FORMULARWERTE, getFormFieldValues());
  }

  /**
   * Serialisiert die aktuellen Werte aller Fomularfelder.
   */
  private String getFormFieldValues()
  {
    // Neues ConfigThingy f�r "Formularwerte"-Abschnitt erzeugen:
    ConfigThingy werte = new ConfigThingy("WM");
    ConfigThingy formwerte = new ConfigThingy("Formularwerte");
    werte.addChild(formwerte);
    Iterator<String> iter = formFieldValues.keySet().iterator();
    while (iter.hasNext())
    {
      String key = iter.next();
      String value = formFieldValues.get(key);
      if (key != null && value != null)
      {
        ConfigThingy entry = new ConfigThingy("");
        ConfigThingy cfID = new ConfigThingy("ID");
        cfID.add(key);
        ConfigThingy cfVALUE = new ConfigThingy("VALUE");
        cfVALUE.add(value);
        entry.addChild(cfID);
        entry.addChild(cfVALUE);
        formwerte.addChild(entry);
      }
    }

    return werte.stringRepresentation();
  }

  /**
   * Liefert den Kontext mit dem die dokumentlokalen Dokumentfunktionen beim Aufruf
   * von getFunctionLibrary() und getDialogLibrary() erzeugt werden.
   * 
   * @author Christoph Lutz (D-III-ITD-5.1)
   */
  synchronized public Map<Object, Object> getFunctionContext()
  {
    return functionContext;
  }

  /**
   * Liefert die Funktionsbibliothek mit den globalen Funktionen des WollMux und den
   * lokalen Funktionen dieses Dokuments.
   * 
   * @author Christoph Lutz (D-III-ITD-5.1)
   */
  synchronized public FunctionLibrary getFunctionLibrary()
  {
    if (functionLib == null)
    {
      ConfigThingy formConf = new ConfigThingy("");
      try
      {
        formConf = getFormDescription().get("Formular");
      }
      catch (NodeNotFoundException e)
      {}
      functionLib =
        WollMuxFiles.parseFunctions(formConf, getDialogLibrary(), functionContext,
          WollMuxSingleton.getInstance().getGlobalFunctions());
    }
    return functionLib;
  }

  /**
   * Liefert die eine Bibliothek mit den globalen Dialogfunktionen des WollMux und
   * den lokalen Dialogfunktionen dieses Dokuments.
   * 
   * @author Christoph Lutz (D-III-ITD-5.1)
   */
  synchronized public DialogLibrary getDialogLibrary()
  {
    if (dialogLib == null)
    {
      ConfigThingy formConf = new ConfigThingy("");
      try
      {
        formConf = getFormDescription().get("Formular");
      }
      catch (NodeNotFoundException e)
      {}
      dialogLib =
        WollMuxFiles.parseFunctionDialogs(formConf,
          WollMuxSingleton.getInstance().getFunctionDialogs(), functionContext);
    }
    return dialogLib;
  }

  /**
   * Erzeugt in der Funktionsbeschreibung eine neue Funktion mit einem automatisch
   * generierten Namen, registriert sie in der Funktionsbibliothek, so dass diese
   * sofort z.B. als TRAFO-Funktion genutzt werden kann und liefert den neuen
   * generierten Funktionsnamen zur�ck oder null, wenn funcConf fehlerhaft ist.
   * 
   * Der automatisch generierte Name ist, nach dem Prinzip
   * PRAEFIX_aktuelleZeitinMillisekunden_zahl aufgebaut. Es wird aber in jedem Fall
   * garantiert, dass der neue Name eindeutig ist und nicht bereits in der
   * Funktionsbibliothek vorkommt.
   * 
   * @param funcConf
   *          Ein ConfigThingy mit dem Aufbau "Bezeichner( FUNKTIONSDEFINITION )",
   *          wobei Bezeichner ein beliebiger Bezeichner ist und FUNKTIONSDEFINITION
   *          ein erlaubter Parameter f�r
   *          {@link de.muenchen.allg.itd51.wollmux.func.FunctionFactory#parse(ConfigThingy, FunctionLibrary, DialogLibrary, Map)},
   *          d.h. der oberste Knoten von FUNKTIONSDEFINITION muss eine erlaubter
   *          Funktionsname, z.B. "AND" sein. Der Bezeichner wird NICHT als Name der
   *          TRAFO verwendet. Stattdessen wird ein neuer eindeutiger TRAFO-Name
   *          generiert.
   * 
   * @author Christoph Lutz (D-III-ITD-5.1)
   */
  private String addLocalAutofunction(ConfigThingy funcConf)
  {
    FunctionLibrary funcLib = getFunctionLibrary();
    DialogLibrary dialogLib = getDialogLibrary();
    Map<Object, Object> context = getFunctionContext();

    // eindeutigen Namen f�r die neue Autofunktion erzeugen:
    Set<String> currentFunctionNames = funcLib.getFunctionNames();
    String name = null;
    for (int i = 0; name == null || currentFunctionNames.contains(name); ++i)
      name = AUTOFUNCTION_PREFIX + System.currentTimeMillis() + "_" + i;

    try
    {
      funcLib.add(name, FunctionFactory.parseChildren(funcConf, funcLib, dialogLib,
        context));

      // Funktion zur Formularbeschreibung hinzuf�gen:
      ConfigThingy betterNameFunc = new ConfigThingy(name);
      for (Iterator<ConfigThingy> iter = funcConf.iterator(); iter.hasNext();)
      {
        ConfigThingy func = iter.next();
        betterNameFunc.addChild(func);
      }
      getFunktionenConf().addChild(betterNameFunc);

      storeCurrentFormDescription();
      return name;
    }
    catch (ConfigurationErrorException e)
    {
      Logger.error(e);
      return null;
    }
  }

  /**
   * Im Vorschaumodus �bertr�gt diese Methode den Formularwert zum Feldes fieldId aus
   * dem persistenten Formularwerte-Abschnitt in die zugeh�rigen Formularfelder im
   * Dokument; Ist der Vorschaumodus nicht aktiv, so werden jeweils nur die
   * Spaltennamen in spitzen Klammern angezeigt; F�r die Aufl�sung der TRAFOs wird
   * dabei die Funktionsbibliothek funcLib verwendet.
   * 
   * @param fieldId
   *          Die ID des Formularfeldes bzw. der Formularfelder, die im Dokument
   *          angepasst werden sollen.
   */
  synchronized public void updateFormFields(String fieldId)
  {
    if (formFieldPreviewMode)
    {
      String value = formFieldValues.get(fieldId);
      if (value == null) value = "";
      setFormFields(fieldId, value, true);
    }
    else
    {
      setFormFields(fieldId, "<" + fieldId + ">", false);
    }
    setDocumentModified(true);
  }

  /**
   * Im Vorschaumodus �bertr�gt diese Methode alle Formularwerte aus dem
   * Formularwerte-Abschnitt der persistenten Daten in die zugeh�rigen Formularfelder
   * im Dokument, wobei evtl. gesetzte Trafo-Funktionen ausgef�hrt werden; Ist der
   * Vorschaumodus nicht aktiv, so werden jeweils nur die Spaltennamen in spitzen
   * Klammern angezeigt.
   */
  private void updateAllFormFields()
  {
    for (Iterator<String> iter = getAllFieldIDs().iterator(); iter.hasNext();)
    {
      String fieldId = iter.next();
      updateFormFields(fieldId);
    }
  }

  /**
   * Setzt den Inhalt aller Formularfelder mit ID fieldId auf value.
   * 
   * @param applyTrafo
   *          gibt an, ob eine evtl. vorhandene TRAFO-Funktion angewendet werden soll
   *          (true) oder nicht (false).
   * @author Matthias Benkmann, Christoph Lutz (D-III-ITD 5.1)
   */
  private void setFormFields(String fieldId, String value, boolean applyTrafo)
  {
    setFormFields(idToFormFields.get(fieldId), value, applyTrafo, false);
    setFormFields(idToTextFieldFormFields.get(fieldId), value, applyTrafo, true);
    setFormFields(staticTextFieldFormFields, value, applyTrafo, true);
  }

  /**
   * Setzt den Inhalt aller Formularfelder aus der Liste formFields auf value.
   * formFields kann null sein, dann passiert nichts.
   * 
   * @param funcLib
   *          Funktionsbibliothek zum Berechnen von TRAFOs. funcLib darf null sein,
   *          dann werden die Formularwerte in jedem Fall untransformiert gesetzt.
   * @param applyTrafo
   *          gibt an ob eine evtl. vorhandenen Trafofunktion verwendet werden soll.
   * @param useKnownFormValues
   *          gibt an, ob die Trafofunktion mit den bekannten Formularwerten (true)
   *          als Parameter, oder ob alle erwarteten Parameter mit dem Wert value
   *          (false) versorgt werden - wird aus Gr�nden der Abw�rtskompatiblit�t zu
   *          den bisherigen insertFormValue-Kommandos ben�tigt.
   * 
   * @author Matthias Benkmann, Christoph Lutz (D-III-ITD 5.1)
   */
  private void setFormFields(List<FormField> formFields, String value,
      boolean applyTrafo, boolean useKnownFormValues)
  {
    if (formFields == null) return;
    Iterator<FormField> fields = formFields.iterator();
    while (fields.hasNext())
    {
      FormField field = fields.next();
      try
      {
        if (applyTrafo)
        {
          String trafoName = field.getTrafoName();
          field.setValue(getTranformedValue(value, trafoName, useKnownFormValues));
        }
        else
          field.setValue(value);
      }
      catch (RuntimeException e)
      {
        // Absicherung gegen das manuelle L�schen von Dokumentinhalten.
      }
    }
  }

  /**
   * Schaltet den Vorschaumodus f�r Formularfelder an oder aus - ist der
   * Vorschaumodus aktiviert, so werden alle Formularfelder mit den zuvor gesetzten
   * Formularwerten angezeigt, ist der Preview-Modus nicht aktiv, so werden nur die
   * Spaltennamen in spitzen Klammern angezeigt.
   * 
   * @param previewMode
   *          true schaltet den Modus an, false schaltet auf den Vorschaumodus zur�ck
   *          in dem die aktuell gesetzten Werte wieder angezeigt werden.
   * 
   * @author Christoph Lutz (D-III-ITD-5.1)
   */
  synchronized public void setFormFieldsPreviewMode(boolean previewMode)
  {
    this.formFieldPreviewMode = previewMode;
    updateAllFormFields();
    cleanupGarbageOfUnreferencedAutofunctions();
  }

  /**
   * Setzt den ViewCursor auf das erste untransformierte Formularfeld, das den
   * Formularwert mit der ID fieldID darstellt. Falls kein untransformiertes
   * Formularfeld vorhanden ist, wird ein transformiertes gew�hlt.
   * 
   * @param fieldId
   *          Die ID des Formularfeldes, das angesprungen werden soll.
   */
  synchronized public void focusFormField(String fieldId)
  {
    FormField field = null;
    List<FormField> formFields = idToTextFieldFormFields.get(fieldId);
    if (formFields != null)
    {
      field = formFields.get(0);
    }
    else
    {
      formFields = idToFormFields.get(fieldId);
      field = preferUntransformedFormField(formFields);
    }

    try
    {
      if (field != null) field.focus();
    }
    catch (RuntimeException e)
    {
      // Absicherung gegen das manuelle L�schen von Dokumentinhalten.
    }
  }

  /**
   * Wenn in der �bergebenen {@link List} mit FormField-Elementen ein
   * nicht-transformiertes Feld vorhanden ist, so wird das erste nicht-transformierte
   * Feld zur�ckgegeben, ansonsten wird das erste transformierte Feld zur�ckgegeben,
   * oder null, falls die Liste keine Elemente enth�lt bzw. null ist.
   * 
   * @param formFields
   *          Liste mit FormField-Elementen
   * @return Ein FormField Element, wobei untransformierte Felder bevorzugt werden.
   */
  protected static FormField preferUntransformedFormField(List<FormField> formFields)
  {
    if (formFields == null) return null;
    Iterator<FormField> iter = formFields.iterator();
    FormField field = null;
    while (iter.hasNext())
    {
      FormField f = iter.next();
      if (field == null) field = f;
      if (f.getTrafoName() == null) return f;
    }
    return field;
  }

  /**
   * Diese Methode berechnet die Transformation des Wertes value mit der
   * Trafofunktion trafoName, die global oder dokumentlokal definiert sein muss;
   * dabei steuert useKnownFormValues, ob der Trafo die Menge aller bekannten
   * Formularwerte als parameter �bergeben wird, oder ob aus Gr�nden der
   * Abw�rtskompatiblilit�t jeder durch die Trafofunktion gelesene Parameter den
   * selben Wert value �bergeben bekommt. Ist trafoName==null, so wird value
   * zur�ckgegeben. Ist die Transformationsionfunktion nicht in der globalen oder
   * dokumentlokalen Funktionsbibliothek enthalten, so wird eine Fehlermeldung
   * zur�ckgeliefert und eine weitere Fehlermeldung in die Log-Datei geschrieben.
   * 
   * @param value
   *          Der zu transformierende Wert.
   * @param trafoName
   *          Der Name der Trafofunktion, der auch null sein darf.
   * @param useKnownFormValues
   *          steuert, ob der Trafo die Menge aller bekannten Formularwerte als
   *          parameter �bergeben wird, oder ob aus Gr�nden der
   *          Abw�rtskompatiblilit�t jeder durch die Trafofunktion gelesene Parameter
   *          den selben Wert value �bergeben bekommt.
   * @return Der transformierte Wert falls das trafoName gesetzt ist und die Trafo
   *         korrekt definiert ist. Ist trafoName==null, so wird value unver�ndert
   *         zur�ckgeliefert. Ist die Funktion trafoName nicht definiert, wird eine
   *         Fehlermeldung zur�ckgeliefert. TESTED
   */
  public String getTranformedValue(String value, String trafoName,
      boolean useKnownFormValues)
  {
    String transformed = value;
    if (trafoName != null)
    {
      Function func = getFunctionLibrary().get(trafoName);
      if (func != null)
      {
        SimpleMap args = new SimpleMap();
        String[] pars = func.parameters();
        for (int i = 0; i < pars.length; i++)
        {
          if (useKnownFormValues)
            args.put(pars[i], formFieldValues.get(pars[i]));
          else
            args.put(pars[i], value);
        }
        transformed = func.getString(args);
      }
      else
      {
        transformed = L.m("<FEHLER: TRAFO '%1' nicht definiert>", trafoName);
        Logger.error(L.m("Die TRAFO '%1' ist nicht definiert.", trafoName));
      }
    }

    return transformed;
  }

  /**
   * Liefert die zu diesem Dokument zugeh�rige FormularGUI, falls dem
   * TextDocumentModel die Existent einer FormGUI �ber setFormGUI(...) mitgeteilt
   * wurde - andernfalls wird null zur�ck geliefert.
   * 
   * @return Die FormularGUI des Formulardokuments oder null
   */
  synchronized public FormModel getFormModel()
  {
    return formModel;
  }

  /**
   * Gibt dem TextDocumentModel die Existent der FormularGUI formGUI bekannt und wird
   * vom DocumentCommandInterpreter in der Methode processFormCommands() gestartet
   * hat, falls das Dokument ein Formulardokument ist.
   * 
   * @param formGUI
   *          Die zu diesem Dokument zugeh�rige formGUI
   */
  synchronized public void setFormModel(FormModel formModel)
  {
    this.formModel = formModel;
  }

  /**
   * Liefert den Frame zu diesem TextDocument oder null, wenn der Frame nicht
   * bestimmt werden kann.
   * 
   * @return
   */
  synchronized public XFrame getFrame()
  {
    try
    {
      return doc.getCurrentController().getFrame();
    }
    catch (java.lang.Exception e)
    {
      return null;
    }
  }

  /**
   * Liefert die Gesamtseitenzahl des Dokuments oder 0, wenn die Seitenzahl nicht
   * bestimmt werden kann.
   * 
   * @return Liefert die Gesamtseitenzahl des Dokuments oder 0, wenn die Seitenzahl
   *         nicht bestimmt werden kann.
   */
  synchronized public int getPageCount()
  {
    try
    {
      return (int) AnyConverter.toLong(UNO.getProperty(doc.getCurrentController(),
        "PageCount"));
    }
    catch (java.lang.Exception e)
    {
      return 0;
    }
  }

  /**
   * Setzt das Fensters des TextDokuments auf Sichtbar (visible==true) oder
   * unsichtbar (visible == false).
   * 
   * @param visible
   */
  synchronized public void setWindowVisible(boolean visible)
  {
    XFrame frame = getFrame();
    if (frame != null)
    {
      frame.getContainerWindow().setVisible(visible);
    }
  }

  /**
   * Liefert true, wenn das Dokument als "modifiziert" markiert ist und damit z.B.
   * die "Speichern?" Abfrage vor dem Schlie�en erscheint.
   * 
   * @param state
   */
  synchronized public boolean getDocumentModified()
  {
    try
    {
      return UNO.XModifiable(doc).isModified();
    }
    catch (java.lang.Exception x)
    {
      return false;
    }
  }

  /**
   * Diese Methode setzt den DocumentModified-Status auf state.
   * 
   * @param state
   */
  synchronized public void setDocumentModified(boolean state)
  {
    try
    {
      UNO.XModifiable(doc).setModified(state);
    }
    catch (java.lang.Exception x)
    {}
  }

  /**
   * Diese Methode blockt/unblocked die Contoller, die f�r das Rendering der
   * Darstellung in den Dokumenten zust�ndig sind, jedoch nur, wenn nicht der
   * debug-modus gesetzt ist.
   * 
   * @param state
   */
  synchronized public void setLockControllers(boolean lock)
  {
    try
    {
      if (WollMuxSingleton.getInstance().isDebugMode() == false
        && UNO.XModel(doc) != null)
      {
        if (lock)
          UNO.XModel(doc).lockControllers();
        else
          UNO.XModel(doc).unlockControllers();
      }
    }
    catch (java.lang.Exception e)
    {}
  }

  /**
   * Setzt die Position des Fensters auf die �bergebenen Koordinaten, wobei die
   * Nachteile der UNO-Methode setWindowPosSize greifen, bei der die Fensterposition
   * nicht mit dem �usseren Fensterrahmen beginnt, sondern mit der grauen Ecke links
   * �ber dem File-Men�.
   * 
   * @param docX
   * @param docY
   * @param docWidth
   * @param docHeight
   */
  synchronized public void setWindowPosSize(int docX, int docY, int docWidth,
      int docHeight)
  {
    try
    {
      getFrame().getContainerWindow().setPosSize(docX, docY, docWidth, docHeight,
        PosSize.POSSIZE);
    }
    catch (java.lang.Exception e)
    {}
  }

  /**
   * Diese Methode setzt den ZoomTyp bzw. den ZoomValue der Dokumentenansicht des
   * Dokuments auf den neuen Wert zoom, der entwender eine ganzzahliger Prozentwert
   * (ohne "%"-Zeichen") oder einer der Werte "Optimal", "PageWidth",
   * "PageWidthExact" oder "EntirePage" ist.
   * 
   * @param zoom
   * @throws ConfigurationErrorException
   */
  private void setDocumentZoom(String zoom) throws ConfigurationErrorException
  {
    Short zoomType = null;
    Short zoomValue = null;

    if (zoom != null)
    {
      // ZOOM-Argument auswerten:
      if (zoom.equalsIgnoreCase("Optimal"))
        zoomType = new Short(DocumentZoomType.OPTIMAL);

      if (zoom.equalsIgnoreCase("PageWidth"))
        zoomType = new Short(DocumentZoomType.PAGE_WIDTH);

      if (zoom.equalsIgnoreCase("PageWidthExact"))
        zoomType = new Short(DocumentZoomType.PAGE_WIDTH_EXACT);

      if (zoom.equalsIgnoreCase("EntirePage"))
        zoomType = new Short(DocumentZoomType.ENTIRE_PAGE);

      if (zoomType == null)
      {
        try
        {
          zoomValue = new Short(zoom);
        }
        catch (NumberFormatException e)
        {}
      }
    }

    // ZoomType bzw ZoomValue setzen:
    Object viewSettings = null;
    try
    {
      viewSettings =
        UNO.XViewSettingsSupplier(doc.getCurrentController()).getViewSettings();
    }
    catch (java.lang.Exception e)
    {}
    if (zoomType != null)
      UNO.setProperty(viewSettings, "ZoomType", zoomType);
    else if (zoomValue != null)
      UNO.setProperty(viewSettings, "ZoomValue", zoomValue);
    else
      throw new ConfigurationErrorException(L.m("Ung�ltiger ZOOM-Wert '%1'", zoom));
  }

  /**
   * Diese Methode liest die (optionalen) Attribute X, Y, WIDTH, HEIGHT und ZOOM aus
   * dem �bergebenen Konfigurations-Abschnitt settings und setzt die
   * Fenstereinstellungen des Dokuments entsprechend um. Bei den P�rchen X/Y bzw.
   * SIZE/WIDTH m�ssen jeweils beide Komponenten im Konfigurationsabschnitt angegeben
   * sein.
   * 
   * @param settings
   *          der Konfigurationsabschnitt, der X, Y, WIDHT, HEIGHT und ZOOM als
   *          direkte Kinder enth�lt.
   */
  synchronized public void setWindowViewSettings(ConfigThingy settings)
  {
    // Fenster holen (zum setzen der Fensterposition und des Zooms)
    XWindow window = null;
    try
    {
      window = getFrame().getContainerWindow();
    }
    catch (java.lang.Exception e)
    {}

    // Insets bestimmen (Rahmenma�e des Windows)
    int insetLeft = 0, insetTop = 0, insetRight = 0, insetButtom = 0;
    if (UNO.XDevice(window) != null)
    {
      DeviceInfo di = UNO.XDevice(window).getInfo();
      insetButtom = di.BottomInset;
      insetTop = di.TopInset;
      insetRight = di.RightInset;
      insetLeft = di.LeftInset;
    }

    // Position setzen:
    try
    {
      int xPos = new Integer(settings.get("X").toString()).intValue();
      int yPos = new Integer(settings.get("Y").toString()).intValue();
      if (window != null)
      {
        window.setPosSize(xPos + insetLeft, yPos + insetTop, 0, 0, PosSize.POS);
      }
    }
    catch (java.lang.Exception e)
    {}
    // Dimensions setzen:
    try
    {
      int width = new Integer(settings.get("WIDTH").toString()).intValue();
      int height = new Integer(settings.get("HEIGHT").toString()).intValue();
      if (window != null)
        window.setPosSize(0, 0, width - insetLeft - insetRight, height - insetTop
          - insetButtom, PosSize.SIZE);
    }
    catch (java.lang.Exception e)
    {}

    // Zoom setzen:
    setDocumentZoom(settings);
  }

  /**
   * Diese Methode setzt den ZoomTyp bzw. den ZoomValue der Dokumentenansicht des
   * Dokuments auf den neuen Wert den das ConfigThingy conf im Knoten ZOOM angibt,
   * der entwender eine ganzzahliger Prozentwert (ohne "%"-Zeichen") oder einer der
   * Werte "Optimal", "PageWidth", "PageWidthExact" oder "EntirePage" ist.
   * 
   * @param zoom
   * @throws ConfigurationErrorException
   */
  synchronized public void setDocumentZoom(ConfigThingy conf)
  {
    try
    {
      setDocumentZoom(conf.get("ZOOM").toString());
    }
    catch (NodeNotFoundException e)
    {
      // ZOOM ist optional
    }
    catch (ConfigurationErrorException e)
    {
      Logger.error(e);
    }
  }

  /**
   * Die Methode f�gt die Formular-Abschnitte aus der Formularbeschreibung der Notiz
   * von formCmd zur aktuellen Formularbeschreibung des Dokuments in den persistenten
   * Daten hinzu und l�scht die Notiz.
   * 
   * @param formCmd
   *          Das formCmd, das die Notzi mit den hinzuzuf�genden Formular-Abschnitten
   *          einer Formularbeschreibung enth�lt.
   * @throws ConfigurationErrorException
   *           Die Notiz der Formularbeschreibung ist nicht vorhanden, die
   *           Formularbeschreibung ist nicht vollst�ndig oder kann nicht geparst
   *           werden.
   */
  synchronized public void addToCurrentFormDescription(DocumentCommand.Form formCmd)
      throws ConfigurationErrorException
  {
    XTextRange range = formCmd.getTextRange();

    XTextContent annotationField =
      UNO.XTextContent(WollMuxSingleton.findAnnotationFieldRecursive(range));
    if (annotationField == null)
      throw new ConfigurationErrorException(
        L.m("Die zugeh�rige Notiz mit der Formularbeschreibung fehlt."));

    Object content = UNO.getProperty(annotationField, "Content");
    if (content == null)
      throw new ConfigurationErrorException(
        L.m("Die zugeh�rige Notiz mit der Formularbeschreibung kann nicht gelesen werden."));

    // Formularbeschreibung �bernehmen und persistent speichern:
    addToFormDescription(getFormDescription(), content.toString());
    storeCurrentFormDescription();

    // Notiz l�schen
    try
    {
      range.getText().removeTextContent(annotationField);
    }
    catch (NoSuchElementException e)
    {
      Logger.error(e);
    }
  }

  /**
   * Versucht das Dokument zu schlie�en, wurde das Dokument jedoch ver�ndert
   * (Modified-Status des Dokuments==true), so erscheint der Dialog
   * "Speichern"/"Verwerfen"/"Abbrechen" �ber den ein sofortiges Schlie�en des
   * Dokuments durch den Benutzer verhindert werden kann. Ist der closeListener
   * registriert (was WollMuxSingleton bereits bei der Erstellung des
   * TextDocumentModels standardm��ig macht), so wird nach dem close() auch
   * automatisch die dispose()-Methode aufgerufen.
   */
  synchronized public void close()
  {
    // Damit OOo vor dem Schlie�en eines ver�nderten Dokuments den
    // save/dismiss-Dialog anzeigt, muss die suspend()-Methode aller
    // XController gestartet werden, die das Model der Komponente enthalten.
    // Man bekommt alle XController �ber die Frames, die der Desktop liefert.
    boolean closeOk = true;
    if (UNO.XFramesSupplier(UNO.desktop) != null)
    {
      XFrame[] frames =
        UNO.XFramesSupplier(UNO.desktop).getFrames().queryFrames(FrameSearchFlag.ALL);
      for (int i = 0; i < frames.length; i++)
      {
        XController c = frames[i].getController();
        if (c != null && UnoRuntime.areSame(c.getModel(), doc))
        {
          // closeOk wird auf false gesetzt, wenn im save/dismiss-Dialog auf die
          // Schaltfl�chen "Speichern" und "Abbrechen" gedr�ckt wird. Bei
          // "Verwerfen" bleibt closeOK unver�ndert (also true).
          if (c.suspend(true) == false) closeOk = false;
        }
      }
    }

    // Wurde das Dokument erfolgreich gespeichert, so merkt dies der Test
    // getDocumentModified() == false. Wurde der save/dismiss-Dialog mit
    // "Verwerfen" beendet, so ist closeOK==true und es wird beendet. Wurde der
    // save/dismiss Dialog abgebrochen, so soll das Dokument nicht geschlossen
    // werden.
    if (closeOk || getDocumentModified() == false)
    {

      // Hier das eigentliche Schlie�en:
      try
      {
        if (UNO.XCloseable(doc) != null) UNO.XCloseable(doc).close(true);
      }
      catch (CloseVetoException e)
      {}

    }
    else if (UNO.XFramesSupplier(UNO.desktop) != null)
    {

      // Tritt in Kraft, wenn "Abbrechen" bet�tigt wurde. In diesem Fall werden
      // die Controllers mit suspend(FALSE) wieder reaktiviert.
      XFrame[] frames =
        UNO.XFramesSupplier(UNO.desktop).getFrames().queryFrames(FrameSearchFlag.ALL);
      for (int i = 0; i < frames.length; i++)
      {
        XController c = frames[i].getController();
        if (c != null && UnoRuntime.areSame(c.getModel(), doc)) c.suspend(false);
      }

    }
  }

  /**
   * Ruft die Dispose-Methoden von allen aktiven, dem TextDocumentModel zugeordneten
   * Dialogen auf und gibt den Speicher des TextDocumentModels frei.
   */
  synchronized public void dispose()
  {
    if (currentMax4000 != null) currentMax4000.dispose();
    currentMax4000 = null;

    if (currentMM != null) currentMM.dispose();
    currentMM = null;

    if (formModel != null) formModel.disposing(this);
    formModel = null;

    // L�scht das TextDocumentModel von doc aus dem WollMux-Singleton.
    WollMuxSingleton.getInstance().disposedTextDocument(doc);
  }

  /**
   * Liefert den Titel des Dokuments, wie er im Fenster des Dokuments angezeigt wird,
   * ohne den Zusatz " - OpenOffice.org Writer" oder "NoTitle", wenn der Titel nicht
   * bestimmt werden kann. TextDocumentModel('<title>')
   */
  synchronized public String getTitle()
  {
    String title = "NoTitle";
    try
    {
      title = UNO.getProperty(getFrame(), "Title").toString();
      // "Untitled1 - OpenOffice.org Writer" -> cut " - OpenOffice.org Writer"
      int i = title.lastIndexOf(" - ");
      if (i >= 0) title = title.substring(0, i);
    }
    catch (java.lang.Exception e)
    {}
    return title;
  }

  /**
   * Liefert eine Stringrepr�sentation des TextDocumentModels - Derzeit in der Form
   * 'doc(<title>)'.
   * 
   * @see java.lang.Object#toString()
   */
  synchronized public String toString()
  {
    return "doc('" + getTitle() + "')";
  }

  /**
   * Registriert genau einen XCloseListener in der Komponente des XTextDocuments, so
   * dass beim Schlie�en des Dokuments die entsprechenden WollMuxEvents ausgef�hrt
   * werden - ist in diesem TextDocumentModel bereits ein XCloseListener registriert,
   * so wird nichts getan.
   */
  private void registerCloseListener()
  {
    if (closeListener == null && UNO.XCloseable(doc) != null)
    {
      closeListener = new XCloseListener()
      {
        public void disposing(EventObject arg0)
        {
          WollMuxEventHandler.handleTextDocumentClosed(doc);
        }

        public void notifyClosing(EventObject arg0)
        {
          WollMuxEventHandler.handleTextDocumentClosed(doc);
        }

        public void queryClosing(EventObject arg0, boolean arg1)
            throws CloseVetoException
        {}
      };
      UNO.XCloseable(doc).addCloseListener(closeListener);
    }
  }

  /**
   * Liefert ein neues zu diesem TextDocumentModel zugeh�rige XPrintModel f�r einen
   * Druckvorgang; ist useDocumentPrintFunctions==true, so werden bereits alle im
   * Dokument gesetzten Druckfunktionen per
   * XPrintModel.usePrintFunctionWithArgument(...) hinzugeladen.
   * 
   * @param useDocPrintFunctions
   *          steuert ob das PrintModel mit den im Dokument gesetzten Druckfunktionen
   *          vorbelegt sein soll.
   * 
   * @author Christoph Lutz (D-III-ITD-5.1)
   */
  synchronized public XPrintModel createPrintModel(boolean useDocPrintFunctions)
  {
    XPrintModel pmod = PrintModels.createPrintModel(this);
    if (useDocPrintFunctions)
    {
      for (Iterator<String> iter = printFunctions.iterator(); iter.hasNext();)
      {
        String name = iter.next();
        try
        {
          pmod.usePrintFunction(name);
        }
        catch (NoSuchMethodException e)
        {
          Logger.error(e);
        }
      }
    }
    return pmod;
  }

  /**
   * Koppelt das AWT-Window window an das Fenster dieses Textdokuments an. Die
   * Methode muss aufgerufen werden, solange das Fenster window unsichtbar und nicht
   * aktiv ist (also z.B. vor dem Aufruf von window.setVisible(true)).
   * 
   * @param window
   *          das Fenster, das an das Hauptfenster angekoppelt werden soll.
   * 
   * @author Christoph Lutz (D-III-ITD-5.1)
   */
  synchronized public void addCoupledWindow(Window window)
  {
    if (window == null) return;
    if (coupledWindowController == null)
    {
      coupledWindowController = new CoupledWindowController();
      XFrame f = getFrame();
      XTopWindow w = null;
      if (f != null) w = UNO.XTopWindow(f.getContainerWindow());
      if (w != null) coupledWindowController.setTopWindow(w);
    }

    coupledWindowController.addCoupledWindow(window);
  }

  /**
   * L�st die Bindung eines angekoppelten Fensters window an das Dokumentfenster.
   * 
   * @param window
   *          das Fenster, dessen Bindung zum Hauptfenster gel�st werden soll. Ist
   *          das Fenster nicht angekoppelt, dann passiert nichts.
   * 
   * @author Christoph Lutz (D-III-ITD-5.1)
   */
  synchronized public void removeCoupledWindow(Window window)
  {
    if (window == null || coupledWindowController == null) return;

    coupledWindowController.removeCoupledWindow(window);

    if (!coupledWindowController.hasCoupledWindows())
    {
      // deregistriert den windowListener.
      XFrame f = getFrame();
      XTopWindow w = null;
      if (f != null) w = UNO.XTopWindow(f.getContainerWindow());
      if (w != null) coupledWindowController.unsetTopWindow(w);
      coupledWindowController = null;
    }
  }

  /**
   * F�gt ein neues Dokumentkommando mit dem Kommandostring cmdStr, der in der Form
   * "WM(...)" erwartet wird, in das Dokument an der TextRange r ein. Dabei wird ein
   * neues Bookmark erstellt und dieses als Dokumenkommando registriert. Dieses
   * Bookmark wird genau �ber r gelegt, so dass abh�ngig vom Dokumentkommando der
   * Inhalt der TextRange r durch eine eventuelle sp�tere Ausf�hrung des
   * Dokumentkommandos �berschrieben wird. cmdStr muss nur das gew�nschte Kommando
   * enthalten ohne eine abschlie�ende Zahl, die zur Herstellung eindeutiger
   * Bookmarks ben�tigt wird - diese Zahl wird bei Bedarf automatisch an den
   * Bookmarknamen angeh�ngt.
   * 
   * @param r
   *          Die TextRange, an der das neue Bookmark mit diesem Dokumentkommando
   *          eingef�gt werden soll. r darf auch null sein und wird in diesem Fall
   *          ignoriert.
   * @param cmdStr
   *          Das Kommando als String der Form "WM(...)".
   * 
   * @author Christoph Lutz (D-III-ITD-5.1)
   */
  synchronized public void addNewDocumentCommand(XTextRange r, String cmdStr)
  {
    documentCommands.addNewDocumentCommand(r, cmdStr);
  }

  /**
   * F�gt an der Stelle r ein neues Textelement vom Typ css.text.TextField.InputUser
   * ein, und verkn�pft das Feld so, dass die Trafo trafo verwendet wird, um den
   * angezeigten Feldwert zu berechnen.
   * 
   * @param r
   *          die Textrange, an der das Feld eingef�gt werden soll
   * @param trafoName
   *          der Name der zu verwendenden Trafofunktion
   * @param hint
   *          Ein Hinweistext, der im Feld angezeigt werden soll, wenn man mit der
   *          Maus dr�ber f�hrt - kann auch null sein, dann wird der Hint nicht
   *          gesetzt.
   * 
   * @author Christoph Lutz (D-III-ITD-5.1)
   */
  synchronized public void addNewInputUserField(XTextRange r, String trafoName,
      String hint)
  {
    try
    {
      ConfigThingy conf = new ConfigThingy("WM");
      conf.add("FUNCTION").add(trafoName);
      String userFieldName = conf.stringRepresentation(false, '\'', false);

      // master erzeugen
      XPropertySet master = getUserFieldMaster(userFieldName);
      if (master == null)
      {
        master =
          UNO.XPropertySet(UNO.XMultiServiceFactory(doc).createInstance(
            "com.sun.star.text.FieldMaster.User"));
        UNO.setProperty(master, "Value", new Integer(0));
        UNO.setProperty(master, "Name", userFieldName);
      }

      // textField erzeugen
      XTextContent f =
        UNO.XTextContent(UNO.XMultiServiceFactory(doc).createInstance(
          "com.sun.star.text.TextField.InputUser"));
      UNO.setProperty(f, "Content", userFieldName);
      if (hint != null) UNO.setProperty(f, "Hint", hint);
      r.getText().insertTextContent(r, f, true);
    }
    catch (java.lang.Exception e)
    {
      Logger.error(e);
    }
  }

  /**
   * Diese Methode entfernt alle Reste, die von nicht mehr referenzierten
   * AUTOFUNCTIONS �brig bleiben: AUTOFUNCTIONS-Definitionen aus der
   * Funktionsbibliothek, der Formularbeschreibung in den persistenten Daten und
   * nicht mehr ben�tigte TextFieldMaster von ehemaligen InputUser-Textfeldern -
   * Durch die Aufr�umaktion �ndert sich der DocumentModified-Status des Dokuments
   * nicht.
   * 
   * @author Christoph Lutz (D-III-ITD-5.1) TESTED
   */
  private void cleanupGarbageOfUnreferencedAutofunctions()
  {
    boolean modified = getDocumentModified();

    // Liste aller derzeit eingesetzten Trafos aufbauen:
    HashSet<String> usedFunctions = new HashSet<String>();
    for (Iterator<String> iter = idToFormFields.keySet().iterator(); iter.hasNext();)
    {
      String id = iter.next();
      List<FormField> l = idToFormFields.get(id);
      for (Iterator<FormField> iterator = l.iterator(); iterator.hasNext();)
      {
        FormField f = iterator.next();
        String trafoName = f.getTrafoName();
        if (trafoName != null) usedFunctions.add(trafoName);
      }
    }
    for (Iterator<String> iter = idToTextFieldFormFields.keySet().iterator(); iter.hasNext();)
    {
      String id = iter.next();
      List<FormField> l = idToTextFieldFormFields.get(id);
      for (Iterator<FormField> iterator = l.iterator(); iterator.hasNext();)
      {
        FormField f = iterator.next();
        String trafoName = f.getTrafoName();
        if (trafoName != null) usedFunctions.add(trafoName);
      }
    }
    for (Iterator<FormField> iterator = staticTextFieldFormFields.iterator(); iterator.hasNext();)
    {
      FormField f = iterator.next();
      String trafoName = f.getTrafoName();
      if (trafoName != null) usedFunctions.add(trafoName);
    }

    // Nicht mehr ben�tigte Autofunctions aus der Funktionsbibliothek l�schen:
    FunctionLibrary funcLib = getFunctionLibrary();
    for (Iterator<String> iter = funcLib.getFunctionNames().iterator(); iter.hasNext();)
    {
      String name = iter.next();
      if (name == null || !name.startsWith(AUTOFUNCTION_PREFIX)
        || usedFunctions.contains(name)) continue;
      funcLib.remove(name);
    }

    // Nicht mehr ben�tigte Autofunctions aus der Formularbeschreibung der
    // persistenten Daten l�schen.
    ConfigThingy functions =
      getFormDescription().query("Formular").query("Funktionen");
    for (Iterator<ConfigThingy> iter = functions.iterator(); iter.hasNext();)
    {
      ConfigThingy funcs = iter.next();
      for (Iterator<ConfigThingy> iterator = funcs.iterator(); iterator.hasNext();)
      {
        String name = iterator.next().getName();
        if (name == null || !name.startsWith(AUTOFUNCTION_PREFIX)
          || usedFunctions.contains(name)) continue;
        iterator.remove();
      }
    }
    storeCurrentFormDescription();

    // Nicht mehr ben�tigte TextFieldMaster von ehemaligen InputUser-Textfeldern
    // l�schen:
    XNameAccess masters = UNO.XTextFieldsSupplier(doc).getTextFieldMasters();
    String prefix = "com.sun.star.text.FieldMaster.User.";
    String[] masterNames = masters.getElementNames();
    for (int i = 0; i < masterNames.length; i++)
    {
      String masterName = masterNames[i];
      if (masterName == null || !masterName.startsWith(prefix)) continue;
      String varName = masterName.substring(prefix.length());
      String trafoName = getFunctionNameForUserFieldName(varName);
      if (trafoName != null && !usedFunctions.contains(trafoName))
      {
        try
        {
          XComponent m = UNO.XComponent(masters.getByName(masterName));
          m.dispose();
        }
        catch (java.lang.Exception e)
        {
          Logger.error(e);
        }
      }
    }

    setDocumentModified(modified);
  }

  /**
   * Diese Methode liefert den TextFieldMaster, der f�r Zugriffe auf das Benutzerfeld
   * mit den Namen userFieldName zust�ndig ist.
   * 
   * @param userFieldName
   * @return den TextFieldMaster oder null, wenn das Benutzerfeld userFieldName nicht
   *         existiert.
   * 
   * @author Christoph Lutz (D-III-ITD-5.1)
   */
  private XPropertySet getUserFieldMaster(String userFieldName)
  {
    XNameAccess masters = UNO.XTextFieldsSupplier(doc).getTextFieldMasters();
    String elementName = "com.sun.star.text.FieldMaster.User." + userFieldName;
    if (masters.hasByName(elementName))
    {
      try
      {
        return UNO.XPropertySet(masters.getByName(elementName));
      }
      catch (java.lang.Exception e)
      {
        Logger.error(e);
      }
    }
    return null;
  }

  /**
   * Wenn das Benutzerfeld mit dem Namen userFieldName vom WollMux interpretiert wird
   * (weil der Name in der Form "WM(FUNCTION '<name>')" aufgebaut ist), dann liefert
   * diese Funktion den Namen <name> der Funktion zur�ck; in allen anderen F�llen
   * liefert die Methode null zur�ck.
   * 
   * @param userFieldName
   *          Name des Benutzerfeldes
   * @return den Namen der in diesem Benutzerfeld verwendeten Funktion oder null,
   *         wenn das Benutzerfeld nicht vom WollMux interpretiert wird.
   * 
   * @author Matthias Benkmann (D-III-ITD-D101)
   */
  public static String getFunctionNameForUserFieldName(String userFieldName)
  {
    if (userFieldName == null) return null;

    Matcher m = InsertionModel4InputUser.INPUT_USER_FUNCTION.matcher(userFieldName);

    if (!m.matches()) return null;
    String confStr = m.group(1);

    ConfigThingy conf;
    try
    {
      conf = new ConfigThingy("INSERT", confStr);
    }
    catch (Exception x)
    {
      return null;
    }

    ConfigThingy trafoConf = conf.query("FUNCTION");
    if (trafoConf.count() != 1)
      return null;
    else
      return trafoConf.toString();
  }

  /**
   * Wenn das als Kommandostring cmdStr �bergebene Dokumentkommando (derzeit nur
   * insertFormValue) eine Trafofunktion gesetzt hat, so wird der Name dieser
   * Funktion zur�ckgeliefert; Bildet cmdStr kein g�ltiges Dokumentkommando ab oder
   * verwendet dieses Dokumentkommando keine Funktion, so wird null zur�ck geliefert.
   * 
   * @param cmdStr
   *          Ein Kommandostring eines Dokumentkommandos in der Form "WM(CMD '<command>'
   *          ...)"
   * @return
   * 
   * @author Christoph Lutz (D-III-ITD-5.1)
   */
  public static String getFunctionNameForDocumentCommand(String cmdStr)
  {
    ConfigThingy wm = new ConfigThingy("");
    try
    {
      wm = new ConfigThingy("", cmdStr).get("WM");
    }
    catch (java.lang.Exception e)
    {}

    String cmd = "";
    try
    {
      cmd = wm.get("CMD").toString();
    }
    catch (NodeNotFoundException e)
    {}

    if (cmd.equalsIgnoreCase("insertFormValue")) try
    {
      return wm.get("TRAFO").toString();
    }
    catch (NodeNotFoundException e)
    {}
    return null;
  }

  /**
   * Ersetzt die aktuelle Selektion (falls vorhanden) durch ein WollMux-Formularfeld
   * mit ID id, dem Hinweistext hint und der durch trafoConf definierten TRAFO. Das
   * Formularfeld ist direkt einsetzbar, d.h. sobald diese Methode zur�ckkehrt, kann
   * �ber setFormFieldValue(id,...) das Feld bef�llt werden. Ist keine Selektion
   * vorhanden, so tut die Funktion nichts.
   * 
   * @param trafoConf
   *          darf null sein, dann wird keine TRAFO gesetzt. Ansonsten ein
   *          ConfigThingy mit dem Aufbau "Bezeichner( FUNKTIONSDEFINITION )", wobei
   *          Bezeichner ein beliebiger Bezeichner ist und FUNKTIONSDEFINITION ein
   *          erlaubter Parameter f�r
   *          {@link de.muenchen.allg.itd51.wollmux.func.FunctionFactory#parse(ConfigThingy, FunctionLibrary, DialogLibrary, Map)},
   *          d.h. der oberste Knoten von FUNKTIONSDEFINITION muss eine erlaubter
   *          Funktionsname, z.B. "AND" sein. Der Bezeichner wird NICHT als Name der
   *          TRAFO verwendet. Stattdessen wird ein neuer eindeutiger TRAFO-Name
   *          generiert.
   * @param hint
   *          Ein Hinweistext der als Tooltip des neuen Formularfeldes angezeigt
   *          werden soll. hint kann null sein, dann wird kein Hinweistext angezeigt.
   * 
   * @author Matthias Benkmann, Christoph Lutz (D-III-ITD 5.1) TESTED
   */
  synchronized public void replaceSelectionWithTrafoField(ConfigThingy trafoConf,
      String hint)
  {
    String trafoName = addLocalAutofunction(trafoConf);

    if (trafoName != null) try
    {
      // Neues UserField an der Cursorposition einf�gen
      addNewInputUserField(getViewCursor(), trafoName, hint);

      // Datenstrukturen aktualisieren
      collectNonWollMuxFormFields();

      // Formularwerte-Abschnitt f�r alle referenzierten fieldIDs vorbelegen
      // wenn noch kein Wert gesetzt ist und Anzeige aktualisieren.
      Function f = getFunctionLibrary().get(trafoName);
      String[] fieldIds = new String[] {};
      if (f != null) fieldIds = f.parameters();
      for (int i = 0; i < fieldIds.length; i++)
      {
        String fieldId = fieldIds[i];
        // Feldwert mit leerem Inhalt vorbelegen, wenn noch kein Wert gesetzt
        // ist.
        if (!formFieldValues.containsKey(fieldId)) setFormFieldValue(fieldId, "");
        updateFormFields(fieldId);
      }

      // Nicht referenzierte Autofunktionen/InputUser-TextFieldMaster l�schen
      cleanupGarbageOfUnreferencedAutofunctions();
    }
    catch (java.lang.Exception e)
    {
      Logger.error(e);
    }
  }

  /**
   * Falls die aktuelle Selektion genau ein Formularfeld enth�lt (die Selektion muss
   * nicht b�ndig mit den Grenzen dieses Feldes sein, aber es darf kein zweites
   * Formularfeld in der Selektion enthalten sein) und dieses eine TRAFO gesetzt hat,
   * so wird die Definition dieser TRAFO als ConfigThingy zur�ckgeliefert, ansonsten
   * null. Wird eine TRAFO gefunden, die in einem globalen Konfigurationsabschnitt
   * definiert ist (also nicht dokumentlokal) und damit auch nicht ver�ndert werden
   * kann, so wird ebenfalls null zur�ck geliefert.
   * 
   * @return null oder die Definition der TRAFO in der Form
   *         "TrafoName(FUNKTIONSDEFINITION)", wobei TrafoName die Bezeichnung ist,
   *         unter der die TRAFO mittels {@link #setTrafo(String, ConfigThingy)}
   *         modifiziert werden kann.
   * @author Matthias Benkmann, Christoph Lutz (D-III-ITD 5.1) TESTED
   */
  synchronized public ConfigThingy getFormFieldTrafoFromSelection()
  {
    XTextCursor vc = getViewCursor();
    if (vc == null) return null;

    HashMap<String, Integer> collectedTrafos = collectTrafosFromEnumeration(vc);

    // Auswertung von collectedTrafos
    HashSet<String> completeFields = new HashSet<String>();
    HashSet<String> startedFields = new HashSet<String>();
    HashSet<String> finishedFields = new HashSet<String>();
    for (Iterator<String> iter = collectedTrafos.keySet().iterator(); iter.hasNext();)
    {
      String trafo = iter.next();
      int complete = collectedTrafos.get(trafo).intValue();
      if (complete == 1) startedFields.add(trafo);
      if (complete == 2) finishedFields.add(trafo);
      if (complete == 3) completeFields.add(trafo);
    }

    // Das Feld ist eindeutig bestimmbar, wenn genau ein vollst�ndiges Feld oder
    // als Fallback genau eine Startmarke gefunden wurde.
    String trafoName = null;
    if (completeFields.size() > 1)
      return null; // nicht eindeutige Felder
    else if (completeFields.size() == 1)
      trafoName = completeFields.iterator().next().toString();
    else if (startedFields.size() > 1)
      return null; // nicht eindeutige Felder
    else if (startedFields.size() == 1)
      trafoName = startedFields.iterator().next().toString();

    // zugeh�riges ConfigThingy aus der Formularbeschreibung zur�ckliefern.
    if (trafoName != null)
      try
      {
        return getFormDescription().query("Formular").query("Funktionen").query(
          trafoName, 2).getLastChild();
      }
      catch (NodeNotFoundException e)
      {}

    return null;
  }

  /**
   * Gibt die Namen aller in der XTextRange gefunden Trafos als Schl�ssel einer
   * HashMap zur�ck. Die zus�tzlichen Integer-Werte in der HashMap geben an, ob (1)
   * nur die Startmarke, (2) nur die Endemarke oder (3) ein vollst�ndiges
   * Bookmark/Feld gefunden wurde (Bei atomaren Feldern wird gleich 3 als Wert
   * gesetzt).
   * 
   * @param textRange
   *          die XTextRange an der gesucht werden soll.
   * 
   * @author Christoph Lutz (D-III-ITD-5.1)
   */
  private static HashMap<String, Integer> collectTrafosFromEnumeration(
      XTextRange textRange)
  {
    HashMap<String, Integer> collectedTrafos = new HashMap<String, Integer>();

    if (textRange == null) return collectedTrafos;
    XEnumerationAccess parEnumAcc =
      UNO.XEnumerationAccess(textRange.getText().createTextCursorByRange(textRange));
    if (parEnumAcc == null) return collectedTrafos;

    XEnumeration parEnum = parEnumAcc.createEnumeration();
    while (parEnum.hasMoreElements())
    {
      XEnumerationAccess porEnumAcc = null;
      try
      {
        porEnumAcc = UNO.XEnumerationAccess(parEnum.nextElement());
      }
      catch (java.lang.Exception e)
      {
        Logger.error(e);
      }
      if (porEnumAcc == null) continue;

      XEnumeration porEnum = porEnumAcc.createEnumeration();
      while (porEnum.hasMoreElements())
      {
        Object portion = null;
        try
        {
          portion = porEnum.nextElement();
        }
        catch (java.lang.Exception e)
        {
          Logger.error(e);
        }

        // InputUser-Textfelder verarbeiten
        XTextField tf = UNO.XTextField(UNO.getProperty(portion, "TextField"));
        if (tf != null
          && UNO.supportsService(tf, "com.sun.star.text.TextField.InputUser"))
        {
          String varName = "" + UNO.getProperty(tf, "Content");
          String t = getFunctionNameForUserFieldName(varName);
          if (t != null) collectedTrafos.put(t, new Integer(3));
        }

        // Dokumentkommandos (derzeit insertFormValue) verarbeiten
        XNamed bm = UNO.XNamed(UNO.getProperty(portion, "Bookmark"));
        if (bm != null)
        {
          String name = "" + bm.getName();

          boolean isStart = false;
          boolean isEnd = false;
          try
          {
            boolean isCollapsed =
              AnyConverter.toBoolean(UNO.getProperty(portion, "IsCollapsed"));
            isStart =
              AnyConverter.toBoolean(UNO.getProperty(portion, "IsStart"))
                || isCollapsed;
            isEnd = !isStart || isCollapsed;
          }
          catch (IllegalArgumentException e)
          {}

          Matcher m = WOLLMUX_BOOKMARK_PATTERN.matcher(name);
          if (m.matches())
          {
            String t = getFunctionNameForDocumentCommand(m.group(1));
            if (t != null)
            {
              Integer s = collectedTrafos.get(t);
              if (s == null) s = new Integer(0);
              if (isStart) s = new Integer(s.intValue() | 1);
              if (isEnd) s = new Integer(s.intValue() | 2);
              collectedTrafos.put(t, s);
            }
          }
        }
      }
    }
    return collectedTrafos;
  }

  /**
   * �ndert die Definition der TRAFO mit Name trafoName auf trafoConf. Die neue
   * Definition wirkt sich sofort auf folgende
   * {@link #setFormFieldValue(String, String)} Aufrufe aus.
   * 
   * @param trafoConf
   *          ein ConfigThingy mit dem Aufbau "Bezeichner( FUNKTIONSDEFINITION )",
   *          wobei Bezeichner ein beliebiger Bezeichner ist und FUNKTIONSDEFINITION
   *          ein erlaubter Parameter f�r
   *          {@link de.muenchen.allg.itd51.wollmux.func.FunctionFactory#parse(ConfigThingy, FunctionLibrary, DialogLibrary, Map)},
   *          d.h. der oberste Knoten von FUNKTIONSDEFINITION muss eine erlaubter
   *          Funktionsname, z.B. "AND" sein. Der Bezeichner wird NICHT verwendet.
   *          Der Name der TRAFO wird ausschlie�lich durch trafoName festgelegt.
   * @throws UnavailableException
   *           wird geworfen, wenn die Trafo trafoName nicht schreibend ver�ndert
   *           werden kann, weil sie z.B. nicht existiert oder in einer globalen
   *           Funktionsbeschreibung definiert ist.
   * @throws ConfigurationErrorException
   *           beim Parsen der Funktion trafoConf trat ein Fehler auf.
   * @author Matthias Benkmann, Christoph Lutz (D-III-ITD 5.1) TESTED
   */
  synchronized public void setTrafo(String trafoName, ConfigThingy trafoConf)
      throws UnavailableException, ConfigurationErrorException
  {
    // Funktionsknoten aus Formularbeschreibung zum Anpassen holen
    ConfigThingy func;
    try
    {
      func =
        getFormDescription().query("Formular").query("Funktionen").query(trafoName,
          2).getLastChild();
    }
    catch (NodeNotFoundException e)
    {
      throw new UnavailableException(e);
    }

    // Funktion parsen und in Funktionsbibliothek setzen:
    FunctionLibrary funcLib = getFunctionLibrary();
    Function function =
      FunctionFactory.parseChildren(trafoConf, funcLib, getDialogLibrary(),
        getFunctionContext());
    funcLib.add(trafoName, function);

    // Kinder von func l�schen, damit sie sp�ter neu gesetzt werden k�nnen
    for (Iterator<ConfigThingy> iter = func.iterator(); iter.hasNext();)
    {
      iter.next();
      iter.remove();
    }

    // Kinder von trafoConf auf func �bertragen
    for (Iterator<ConfigThingy> iter = trafoConf.iterator(); iter.hasNext();)
    {
      ConfigThingy f = iter.next();
      func.addChild(new ConfigThingy(f));
    }

    // neue Formularbeschreibung sichern
    storeCurrentFormDescription();

    // Die neue Funktion kann von anderen IDs abh�ngen als die bisherige
    // Funktion. Hier muss daf�r gesorgt werden, dass in idToTextFieldFormFields
    // veraltete ID-Zuordnungen gel�scht und neue ID-Zuordungen eingetragen
    // werden. Am einfachsten macht dies vermutlich ein
    // collectNonWollMuxFormFields(). InsertFormValue-Dokumentkommandos haben
    // eine feste ID-Zuordnung und kommen aus dieser auch nicht aus. D.h.
    // InsertFormValue-Bookmarks m�ssen nicht aktualisiert werden.
    collectNonWollMuxFormFields();

    // Felder updaten:
    updateAllFormFields();
  }

  /**
   * Diese Methode liefert ein Array von FieldInfo-Objekten, das Informationen �ber
   * FeldIDs enth�lt, die in der aktuellen Selektion (falls der ViewCursor eine
   * Selektion aufspannt) oder im gesamten Dokument in insertFormValue-Kommandos,
   * Serienbrieffelder, Benutzerfelder und evtl. gesetzten Trafos referenziert werden
   * und nicht im Schema schema aufgef�hrt sind. Ist eine Selektion vorhanden, so ist
   * die Liste in der Reihenfolge aufgebaut, in der die IDs im Dokument angesprochen
   * werden. Ist keine Selektion vorhanden, so werden die Felder in alphabetisch
   * sortierter Reihenfolge zur�ckgeliefert.
   * 
   * @return Eine Liste aller Referenzierten FeldIDs, des Dokuments oder der
   *         aktuellen Selektion, die nicht in schema enthalten sind.
   * 
   * @author Christoph Lutz (D-III-ITD-5.1)
   */
  synchronized public ReferencedFieldID[] getReferencedFieldIDsThatAreNotInSchema(
      Set<String> schema)
  {
    ArrayList<ReferencedFieldID> list = new ArrayList<ReferencedFieldID>();
    if (hasSelection())
    {
      // Nur Felder der aktuellen Selektion zur�ckliefern.
      // TODO: diesen Fall implementieren
    }
    else
    {
      // Alle ReferencedFieldIDs des Dokuments alphabetisch sortiert
      // zur�ckliefern.
      List<String> sortedIDs = new ArrayList<String>(getAllFieldIDs());
      Collections.sort(sortedIDs);
      for (Iterator<String> iter = sortedIDs.iterator(); iter.hasNext();)
      {
        String id = iter.next();
        if (schema.contains(id)) continue;
        List<FormField> fields = new ArrayList<FormField>();
        if (idToFormFields.containsKey(id)) fields.addAll(idToFormFields.get(id));
        if (idToTextFieldFormFields.containsKey(id))
          fields.addAll(idToTextFieldFormFields.get(id));
        boolean hasTrafo = false;
        for (Iterator<FormField> fieldIter = fields.iterator(); fieldIter.hasNext();)
        {
          FormField field = fieldIter.next();
          if (field.getTrafoName() != null) hasTrafo = true;
        }
        list.add(new ReferencedFieldID(id, hasTrafo));
      }
    }

    // Array FieldInfo erstellen
    ReferencedFieldID[] fieldInfos = new ReferencedFieldID[list.size()];
    int i = 0;
    for (Iterator<ReferencedFieldID> iter = list.iterator(); iter.hasNext();)
    {
      ReferencedFieldID fieldInfo = iter.next();
      fieldInfos[i++] = fieldInfo;
    }
    return fieldInfos;
  }

  /**
   * Enth�lt Informationen �ber die in der Selektion oder im gesamten Dokument in
   * Feldern referenzierten FeldIDs.
   * 
   * @author Christoph Lutz (D-III-ITD-5.1)
   */
  public static class ReferencedFieldID
  {
    private final String fieldId;

    private final boolean isTransformed;

    public ReferencedFieldID(String fieldId, boolean isTransformed)
    {
      this.fieldId = fieldId;
      this.isTransformed = isTransformed;
    }

    /**
     * Liefert die FieldID als String.
     * 
     * @author Christoph Lutz (D-III-ITD-5.1)
     */
    public String getFieldId()
    {
      return fieldId;
    }

    /**
     * Liefert true, wenn das Feld TODO: comment FieldInfo.isTransformed
     * 
     * @return
     * 
     * @author Christoph Lutz (D-III-ITD-5.1)
     */
    public boolean isTransformed()
    {
      return isTransformed;
    }
  }

  /**
   * Diese Methode ersetzt die Referenzen der ID fieldId im gesamten Dokument durch
   * neue IDs, die in der Ersetzungsregel subst spezifiziert sind. Die
   * Ersetzungsregel ist vom Typ FieldSubstitution und kann mehrere Elemente (fester
   * Text oder Felder) enthalten, die an Stelle eines alten Feldes gesetzt werden
   * sollen. Damit kann eine Ersetzungsregel auch daf�r sorgen, dass aus einem fr�her
   * atomaren Feld in Zukunft mehrere Felder entstehen. Folgender Abschnitt
   * beschreibt, wie sich die Ersetzung auf verschiedene Elemente auswirkt.
   * 
   * 1) Ersetzungsregel "&lt;neueID&gt;" - Einfache Ersetzung mit genau einem neuen
   * Serienbrieffeld (z.B. "&lt;Vorname&gt;"): bei insertFormValue-Kommandos wird
   * WM(CMD'insertFormValue' ID '&lt;alteID&gt;' [TRAFO...]) ersetzt durch WM(CMD
   * 'insertFormValue' ID '&lt;neueID&gt;' [TRAFO...]). Bei Serienbrieffeldern wird
   * die ID ebenfalls direkt ersetzt durch &lt;neueID&gt;. Bei
   * WollMux-Benutzerfeldern, die ja immer eine Trafo hinterlegt haben, wird jede
   * vorkommende Funktion VALUE 'alteID' ersetzt durch VALUE 'neueID'.
   * 
   * 2) Ersetzungsregel "&lt;A&gt; &lt;B&gt;" - Kompexe Ersetzung mit mehreren neuen
   * IDs und Text: Diese Ersetzung ist bei transformierten Feldern grunds�tzlich
   * nicht zugelassen. Ein bestehendes insertFormValue-Kommando ohne Trafo wird wie
   * folgt manipuliert: anstelle des alten Bookmarks WM(CMD'insertFormValue' ID
   * 'alteId') wird der entsprechende Freitext und entsprechende neue
   * WM(CMD'insertFormValue' ID 'neueIDn') Bookmarks in den Text eingef�gt. Ein
   * Serienbrieffeld wird ersetzt durch entsprechende neue Serienbrieffelder, die
   * durch den entsprechenden Freitext getrennt sind.
   * 
   * 3) Leere Ersetzungsregel - in diesem Fall wird keine Ersetzung vorgenommen und
   * die Methode kehrt sofort zur�ck.
   * 
   * In allen F�llen gilt, dass die �nderung nach Ausf�hrung dieser Methode sofort
   * aktiv sind und der Aufruf von setFormFieldValue(...) bzw. updateFormFields(...)
   * mit den neuen IDs direkt in den ver�nderten Feldern Wirkung zeigt. Ebenso werden
   * aus dem Formularwerte-Abschnitt in den persistenten Daten die alten Werte der
   * ersetzten IDs gel�scht.
   * 
   * @param fieldId
   *          Feld, das mit Hilfe der Ersetzungsregel subst ersetzt werden soll.
   * @param subst
   *          die Ersetzungsregel, die beschreibt, welche Inhalte an Stelle des alten
   *          Feldes eingesetzt werden sollen.
   * 
   * @author Christoph Lutz (D-III-ITD-5.1) TESTED
   */
  synchronized public void applyFieldSubstitution(String fieldId,
      FieldSubstitution subst)
  {
    // keine Ersetzung, wenn subst leer ist.
    if (!subst.iterator().hasNext()) return;

    // enth�lt sp�ter die neue FieldId, wenn eine 1-zu-1-Zuordnung vorliegt
    String newFieldId = null;

    // Neuen Text zusammenbauen, Felder sind darin mit <feldname> gekennzeichnet
    String substStr = "";
    int count = 0;
    for (Iterator<FieldSubstitution.SubstElement> substIter = subst.iterator(); substIter.hasNext();)
    {
      FieldSubstitution.SubstElement ele = substIter.next();
      if (ele.isFixedText())
      {
        substStr += ele.getValue();
      }
      else if (ele.isField())
      {
        substStr += "<" + ele.getValue() + ">";
        newFieldId = ele.getValue();
      }
      count++;
    }
    if (count != 1) newFieldId = null;

    // Alle InsertFormValue-Felder anpassen:
    List<FormField> c = idToFormFields.get(fieldId);
    if (c != null)
    {
      for (Iterator<FormField> iter = c.iterator(); iter.hasNext();)
      {
        FormField f = iter.next();
        if (f.getTrafoName() != null)
        {
          // Transformierte Felder soweit m�glich behandeln
          if (newFieldId != null)
            // 1-zu-1 Zuordnung: Hier kann substitueFieldID verwendet werden
            f.substituteFieldID(fieldId, newFieldId);
          else
            Logger.error(L.m("Kann transformiertes Feld nur durch eine 1-zu-1 Zuordnung ersetzen."));
        }
        else
        {
          // Untransformierte Felder durch neue Felder ersetzen
          XTextRange anchor = f.getAnchor();
          if (f.getAnchor() != null)
          {
            // Cursor erzeugen, Formularfeld l�schen und neuen String setzen
            XTextCursor cursor = anchor.getText().createTextCursorByRange(anchor);
            f.dispose();
            cursor.setString(substStr);

            // Neue Bookmarks passend zum Text platzieren
            cursor.collapseToStart();
            for (Iterator<FieldSubstitution.SubstElement> substIter =
              subst.iterator(); substIter.hasNext();)
            {
              FieldSubstitution.SubstElement ele = substIter.next();
              if (ele.isFixedText())
              {
                cursor.goRight((short) ele.getValue().length(), false);
              }
              else if (ele.isField())
              {
                cursor.goRight((short) (1 + ele.getValue().length() + 1), true);
                new Bookmark(
                  "WM(CMD 'insertFormValue' ID '" + ele.getValue() + "')", doc,
                  cursor);
                cursor.collapseToEnd();
              }
            }
          }
        }
      }
    }

    // Alle Datenbank- und Benutzerfelder anpassen:
    c = idToTextFieldFormFields.get(fieldId);
    if (c != null)
    {
      for (Iterator<FormField> iter = c.iterator(); iter.hasNext();)
      {
        FormField f = iter.next();
        if (f.getTrafoName() != null)
        {
          // Transformierte Felder soweit m�glich behandeln
          if (newFieldId != null)
            // 1-zu-1 Zuordnung: hier kann f.substitueFieldId nicht verwendet
            // werden, daf�r kann aber die Trafo angepasst werden.
            substituteFieldIdInTrafo(f.getTrafoName(), fieldId, newFieldId);
          else
            Logger.error(L.m("Kann transformiertes Feld nur durch eine 1-zu-1 Zuordnung ersetzen."));
        }
        else
        {
          // Untransformierte Felder durch neue Felder ersetzen
          XTextRange anchor = f.getAnchor();
          if (f.getAnchor() != null)
          {
            // Cursor �ber den Anker erzeugen und Formularfeld l�schen
            XTextCursor cursor = anchor.getText().createTextCursorByRange(anchor);
            f.dispose();
            cursor.setString(substStr);

            // Neue Datenbankfelder passend zum Text einf�gen
            cursor.collapseToStart();
            for (Iterator<FieldSubstitution.SubstElement> substIter =
              subst.iterator(); substIter.hasNext();)
            {
              FieldSubstitution.SubstElement ele = substIter.next();
              if (ele.isFixedText())
              {
                cursor.goRight((short) ele.getValue().length(), false);
              }
              else if (ele.isField())
              {
                cursor.goRight((short) (1 + ele.getValue().length() + 1), true);
                insertMailMergeField(ele.getValue(), cursor);
                cursor.collapseToEnd();
              }
            }
          }
        }
      }
    }

    // Datenstrukturen aktualisieren
    getDocumentCommands().update();
    // collectNonWollMuxFormFields() wird im folgenden scan auch noch erledigt
    new DocumentCommandInterpreter(this).scanGlobalDocumentCommands();

    // Alte Formularwerte aus den persistenten Daten entfernen
    setFormFieldValue(fieldId, null);

    // Ansicht der betroffenen Felder aktualisieren
    for (Iterator<FieldSubstitution.SubstElement> iter = subst.iterator(); iter.hasNext();)
    {
      FieldSubstitution.SubstElement ele = iter.next();
      if (ele.isField()) updateFormFields(ele.getValue());
    }
  }

  /**
   * Diese Methode ersetzt jedes Vorkommen von VALUE "oldFieldId" in der
   * dokumentlokalen Trafo-Funktion trafoName durch VALUE "newFieldId", speichert die
   * neue Formularbeschreibung persistent im Dokument ab und passt die aktuelle
   * Funktionsbibliothek entsprechend an. Ist einer der Werte trafoName, oldFieldId
   * oder newFieldId null, dann macht diese Methode nichts.
   * 
   * @param trafoName
   *          Die Funktion, in der die Ersetzung vorgenommen werden soll.
   * @param oldFieldId
   *          Die alte Feld-ID, die durch newFieldId ersetzt werden soll.
   * @param newFieldId
   *          die neue Feld-ID, die oldFieldId ersetzt.
   * 
   * @author Christoph Lutz (D-III-ITD-5.1) TESTED
   */
  private void substituteFieldIdInTrafo(String trafoName, String oldFieldId,
      String newFieldId)
  {
    if (trafoName == null || oldFieldId == null || newFieldId == null) return;
    try
    {
      ConfigThingy trafoConf =
        getFormDescription().query("Formular").query("Funktionen").query(trafoName,
          2).getLastChild();
      substituteValueRecursive(trafoConf, oldFieldId, newFieldId);

      // neue Formularbeschreibung persistent machen
      storeCurrentFormDescription();

      // Funktion neu parsen und Funktionsbibliothek anpassen
      FunctionLibrary funcLib = getFunctionLibrary();
      try
      {
        Function func =
          FunctionFactory.parseChildren(trafoConf, funcLib, dialogLib,
            getFunctionContext());
        getFunctionLibrary().add(trafoName, func);
      }
      catch (ConfigurationErrorException e)
      {
        // sollte eigentlich nicht auftreten, da die alte Trafo ja auch schon
        // einmal erfolgreich geparsed werden konnte.
        Logger.error(e);
      }
    }
    catch (NodeNotFoundException e)
    {
      Logger.error(L.m(
        "Die trafo '%1' ist nicht in diesem Dokument definiert und kann daher nicht ver�ndert werden.",
        trafoName));
    }
  }

  /**
   * Durchsucht das ConfigThingy conf rekursiv und ersetzt alle VALUE-Knoten, die
   * genau ein Kind besitzen durch VALUE-Knoten mit dem neuen Kind newId.
   * 
   * @param conf
   *          Das ConfigThingy, in dem rekursiv ersetzt wird.
   * 
   * @author Christoph Lutz (D-III-ITD-5.1)
   */
  private void substituteValueRecursive(ConfigThingy conf, String oldFieldId,
      String newFieldId)
  {
    if (conf == null) return;

    if (conf.getName().equals("VALUE") && conf.count() == 1
      && conf.toString().equals(oldFieldId))
    {
      try
      {
        conf.getLastChild().setName(newFieldId);
      }
      catch (NodeNotFoundException e)
      {
        // kann wg. der obigen Pr�fung nicht auftreten.
      }
      return;
    }

    for (Iterator<ConfigThingy> iter = conf.iterator(); iter.hasNext();)
    {
      ConfigThingy child = iter.next();
      substituteValueRecursive(child, oldFieldId, newFieldId);
    }
  }

  /**
   * Diese Klasse beschreibt die Ersetzung eines bestehendes Formularfeldes durch
   * neue Felder oder konstante Textinhalte. Sie liefert einen Iterator, �ber den die
   * einzelnen Elemente (Felder bzw. fester Text) vom Typ SubstElement iteriert
   * werden k�nnen.
   * 
   * @author Christoph Lutz (D-III-ITD-5.1)
   */
  public static class FieldSubstitution
  {
    private List<SubstElement> list = new ArrayList<SubstElement>();

    public void addField(String fieldname)
    {
      list.add(new SubstElement(SubstElement.FIELD, fieldname));
    }

    public void addFixedText(String text)
    {
      list.add(new SubstElement(SubstElement.FIXED_TEXT, text));
    }

    public Iterator<SubstElement> iterator()
    {
      return list.iterator();
    }

    private static class SubstElement
    {
      private static final int FIXED_TEXT = 0;

      private static final int FIELD = 1;

      private int type;

      private String value;

      public SubstElement(int type, String value)
      {
        this.value = value;
        this.type = type;
      }

      public String getValue()
      {
        return value;
      }

      public boolean isField()
      {
        return type == FIELD;
      }

      public boolean isFixedText()
      {
        return type == FIXED_TEXT;
      }
    }
  }
}
