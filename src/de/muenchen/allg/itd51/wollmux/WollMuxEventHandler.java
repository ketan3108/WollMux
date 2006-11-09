/*
 * Dateiname: WollMuxEventHandler.java
 * Projekt  : WollMux
 * Funktion : Erm�glicht die Einstellung neuer WollMuxEvents in die EventQueue.
 * 
 * Copyright: Landeshauptstadt M�nchen
 *
 * �nderungshistorie:
 * Datum      | Wer | �nderungsgrund
 * -------------------------------------------------------------------
 * 24.10.2005 | LUT | Erstellung als EventHandler.java
 * 01.12.2005 | BNK | +on_unload() das die Toolbar neu erzeugt (b�ser Hack zum 
 *                  | Beheben des Seitenansicht-Toolbar-Verschwindibus-Problems)
 *                  | Ausgabe des hashCode()s in den Debug-Meldungen, um Events 
 *                  | Objekten zuordnen zu k�nnen beim Lesen des Logfiles
 * 27.03.2005 | LUT | neues Kommando openDocument
 * 21.04.2006 | LUT | +ConfigurationErrorException statt NodeNotFoundException bei
 *                    fehlendem URL-Attribut in Textfragmenten
 * 06.06.2006 | LUT | + Abl�sung der Event-Klasse durch saubere Objektstruktur
 *                    + �berarbeitung vieler Fehlermeldungen
 *                    + Zeilenumbr�che in showInfoModal, damit keine unlesbaren
 *                      Fehlermeldungen mehr ausgegeben werden.
 * -------------------------------------------------------------------
 *
 * @author Christoph Lutz (D-III-ITD 5.1)
 * @version 1.0
 * 
 */
package de.muenchen.allg.itd51.wollmux;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import com.sun.star.awt.XWindow;
import com.sun.star.beans.PropertyValue;
import com.sun.star.frame.DispatchResultEvent;
import com.sun.star.frame.XController;
import com.sun.star.frame.XDispatch;
import com.sun.star.frame.XDispatchResultListener;
import com.sun.star.frame.XFrame;
import com.sun.star.frame.XNotifyingDispatch;
import com.sun.star.lang.EventObject;
import com.sun.star.lang.XComponent;
import com.sun.star.text.XTextCursor;
import com.sun.star.text.XTextDocument;
import com.sun.star.text.XTextRange;
import com.sun.star.uno.Exception;
import com.sun.star.uno.RuntimeException;
import com.sun.star.uno.UnoRuntime;
import com.sun.star.uno.XComponentContext;

import de.muenchen.allg.afid.UNO;
import de.muenchen.allg.afid.UnoProps;
import de.muenchen.allg.afid.UnoService;
import de.muenchen.allg.itd51.parser.ConfigThingy;
import de.muenchen.allg.itd51.parser.NodeNotFoundException;
import de.muenchen.allg.itd51.wollmux.FormFieldFactory.FormField;
import de.muenchen.allg.itd51.wollmux.TextDocumentModel.PrintFailedException;
import de.muenchen.allg.itd51.wollmux.db.ColumnNotFoundException;
import de.muenchen.allg.itd51.wollmux.db.DJDataset;
import de.muenchen.allg.itd51.wollmux.db.DJDatasetListElement;
import de.muenchen.allg.itd51.wollmux.db.Dataset;
import de.muenchen.allg.itd51.wollmux.db.DatasourceJoiner;
import de.muenchen.allg.itd51.wollmux.db.QueryResults;
import de.muenchen.allg.itd51.wollmux.dialog.AbsenderAuswaehlen;
import de.muenchen.allg.itd51.wollmux.dialog.Common;
import de.muenchen.allg.itd51.wollmux.dialog.Dialog;
import de.muenchen.allg.itd51.wollmux.dialog.PersoenlicheAbsenderlisteVerwalten;
import de.muenchen.allg.itd51.wollmux.former.FormularMax4000;
import de.muenchen.allg.itd51.wollmux.func.FunctionLibrary;
import de.muenchen.allg.itd51.wollmux.func.PrintFunction;

/**
 * Erm�glicht die Einstellung neuer WollMuxEvents in die EventQueue.
 * 
 * @author Christoph Lutz (D-III-ITD 5.1)
 */
public class WollMuxEventHandler
{
  /**
   * Mit dieser Methode ist es m�glich die Entgegennahme von Events zu
   * blockieren. Alle eingehenden Events werden ignoriert, wenn accept auf false
   * gesetzt ist und entgegengenommen, wenn accept auf true gesetzt ist.
   * 
   * @param accept
   */
  public static void setAcceptEvents(boolean accept)
  {
    EventProcessor.getInstance().setAcceptEvents(accept);
  }

  /**
   * Der EventProcessor sorgt f�r eine synchronisierte Verarbeitung aller
   * Wollmux-Events. Alle Events werden in eine synchronisierte eventQueue
   * hineingepackt und von einem einzigen eventProcessingThread sequentiell
   * abgearbeitet.
   * 
   * @author lut
   */
  public static class EventProcessor
  {
    /**
     * Gibt an, ob der EventProcessor �berhaupt events entgegennimmt. Ist
     * acceptEvents=false, werden alle Events ignoriert.
     */
    private boolean acceptEvents = false;

    private List eventQueue = new LinkedList();

    private static EventProcessor singletonInstance;

    private static Thread eventProcessorThread;

    private static EventProcessor getInstance()
    {
      if (singletonInstance == null) singletonInstance = new EventProcessor();
      return singletonInstance;
    }

    /**
     * Mit dieser Methode ist es m�glich die Entgegennahme von Events zu
     * blockieren. Alle eingehenden Events werden ignoriert, wenn accept auf
     * false gesetzt ist und entgegengenommen, wenn accept auf true gesetzt ist.
     * 
     * @param accept
     */
    private void setAcceptEvents(boolean accept)
    {
      acceptEvents = accept;
      if (accept)
        Logger.debug("EventProcessor: akzeptiere neue Events.");
      else
        Logger.debug("EventProcessor: blockiere Entgegennahme von Events!");
    }

    private EventProcessor()
    {
      // starte den eventProcessorThread
      eventProcessorThread = new Thread(new Runnable()
      {
        public void run()
        {
          Logger.debug("Starte EventProcessor-Thread");
          try
          {
            while (true)
            {
              WollMuxEvent event;
              synchronized (eventQueue)
              {
                while (eventQueue.isEmpty())
                  eventQueue.wait();
                event = (WollMuxEvent) eventQueue.remove(0);
              }

              event.process();
            }
          }
          catch (InterruptedException e)
          {
            Logger.error("EventProcessor-Thread wurde unterbrochen:");
            Logger.error(e);
          }
          Logger.debug("Beende EventProcessor-Thread");
        }
      });
      eventProcessorThread.start();
    }

    /**
     * Diese Methode f�gt ein Event an die eventQueue an wenn der WollMux
     * erfolgreich initialisiert wurde und damit events akzeptieren darf.
     * Anschliessend weckt sie den EventProcessor-Thread.
     * 
     * @param event
     */
    private void addEvent(WollMuxEventHandler.WollMuxEvent event)
    {
      if (acceptEvents) synchronized (eventQueue)
      {
        eventQueue.add(event);
        eventQueue.notifyAll();
      }
    }
  }

  /**
   * Interface f�r die Events, die dieser EventHandler abarbeitet.
   */
  public interface WollMuxEvent
  {
    /**
     * Startet die Ausf�hrung des Events und darf nur aus dem EventProcessor
     * aufgerufen werden.
     */
    public void process();

    /**
     * Gibt an, ob das Event eine Referenz auf das Objekt o, welches auch ein
     * UNO-Service sein kann, enth�lt.
     */
    public boolean requires(Object o);
  }

  /**
   * Repr�sentiert einen Fehler, der benutzersichtbar in einem Fehlerdialog
   * angezeigt wird.
   * 
   * @author christoph.lutz
   */
  private static class WollMuxFehlerException extends java.lang.Exception
  {
    private static final long serialVersionUID = 3618646713098791791L;

    public WollMuxFehlerException(String msg)
    {
      super(msg);
    }

    public WollMuxFehlerException(String msg, java.lang.Exception e)
    {
      super(msg, e);
    }
  }

  private static class CantStartDialogException extends WollMuxFehlerException
  {
    private static final long serialVersionUID = -1130975078605219254L;

    public CantStartDialogException(java.lang.Exception e)
    {
      super("Der Dialog konnte nicht gestartet werden!\n\n"
            + "Bitte kontaktieren Sie Ihre Systemadministration.", e);
    }
  }

  /**
   * Dient als Basisklasse f�r konkrete Event-Implementierungen.
   */
  private static class BasicEvent implements WollMuxEvent
  {

    private boolean[] lock = new boolean[] { true };

    /**
     * Diese Method ist f�r die Ausf�hrung des Events zust�ndig. Nach der
     * Bearbeitung entscheidet der R�ckgabewert ob unmittelbar die Bearbeitung
     * des n�chsten Events gestartet werden soll oder ob das GUI blockiert
     * werden soll bis das n�chste actionPerformed-Event beim EventProcessor
     * eintrifft.
     */
    public void process()
    {
      Logger.debug("Process WollMuxEvent " + this.toString());
      try
      {
        doit();
      }
      catch (WollMuxFehlerException e)
      {
        errorMessage(e);
      }
      // Notnagel f�r alle Runtime-Exceptions.
      catch (Throwable t)
      {
        Logger.error(t);
      }
    }

    /**
     * Logged die �bergebene Fehlermeldung nach Logger.error() und erzeugt ein
     * Dialogfenster mit der Fehlernachricht.
     */
    protected void errorMessage(Throwable t)
    {
      Logger.error(t);
      String msg = "";
      if (t.getMessage() != null) msg += t.getMessage();
      Throwable c = t.getCause();
      if (c != null)
      {
        msg += "\n\n" + c;
      }
      showInfoModal("WollMux-Fehler", msg);
    }

    /**
     * Jede abgeleitete Event-Klasse sollte die Methode doit redefinieren, in
     * der die eigentlich event-Bearbeitung erfolgt. Die Methode doit muss alle
     * auftretenden Exceptions selbst behandeln, Fehler die jedoch
     * benutzersichtbar in einem Dialog angezeigt werden sollen, k�nnen �ber
     * eine WollMuxFehlerException nach oben weitergereicht werden.
     */
    protected void doit() throws WollMuxFehlerException
    {
    };

    /*
     * (non-Javadoc)
     * 
     * @see de.muenchen.allg.itd51.wollmux.WollMuxEventHandler.WollMuxEvent#requires(java.lang.Object)
     */
    public boolean requires(Object o)
    {
      return false;
    }

    /**
     * Wenn in dem �bergebenen Vector mit FormField-Elementen ein
     * nicht-transformiertes Feld vorhanden ist, so wird das erste
     * nicht-transformierte Feld zur�ckgegeben, ansonsten wird das erste
     * transformierte Feld zur�ckgegeben, oder null, falls der Vector keine
     * Elemente enth�lt.
     * 
     * @param formFields
     *          Vektor mit FormField-Elementen
     * @return Ein FormField Element, wobei untransformierte Felder bevorzugt
     *         werden.
     */
    protected static FormField preferUntransformedFormField(Vector formFields)
    {
      Iterator iter = formFields.iterator();
      FormField field = null;
      while (iter.hasNext())
      {
        FormField f = (FormField) iter.next();
        if (field == null) field = f;
        if (!f.hasTrafo()) return f;
      }
      return field;
    }

    public String toString()
    {
      return this.getClass().getSimpleName();
    }

    /**
     * Setzt einen lock, der in Verbindung mit setUnlock und der
     * waitForUnlock-Methode verwendet werden kann, um quasi Modalit�t f�r nicht
     * modale Dialoge zu realisieren. setLock() sollte stets vor dem Aufruf des
     * nicht modalen Dialogs erfolgen, nach dem Aufruf des nicht modalen Dialogs
     * folgt der Aufruf der waitForUnlock()-Methode. Der nicht modale Dialog
     * erzeugt bei der Beendigung ein ActionEvent, das daf�r sorgt, dass
     * setUnlock aufgerufen wird.
     */
    protected void setLock()
    {
      lock[0] = true;
    }

    /**
     * Macht einen mit setLock() gesetzten Lock r�ckg�ngig und bricht damit eine
     * evtl. wartende waitForUnlock()-Methode ab.
     */
    protected void setUnlock()
    {
      synchronized (lock)
      {
        lock[0] = false;
        lock.notifyAll();
      }
    }

    /**
     * Wartet so lange, bis der vorher mit setLock() gesetzt lock mit der
     * Methode setUnlock() aufgehoben wird. So kann die quasi Modalit�t nicht
     * modale Dialoge zu realisiert werden. setLock() sollte stets vor dem
     * Aufruf des nicht modalen Dialogs erfolgen, nach dem Aufruf des nicht
     * modalen Dialogs folgt der Aufruf der waitForUnlock()-Methode. Der nicht
     * modale Dialog erzeugt bei der Beendigung ein ActionEvent, das daf�r
     * sorgt, dass setUnlock aufgerufen wird.
     */
    protected void waitForUnlock()
    {
      try
      {
        synchronized (lock)
        {
          while (lock[0] == true)
            lock.wait();
        }
      }
      catch (InterruptedException e)
      {
      }
    }

    /**
     * Dieser ActionListener kann nicht modalen Dialogen �bergeben werden und
     * sorgt in Verbindung mit den Methoden setLock() und waitForUnlock() daf�r,
     * dass quasi modale Dialoge realisiert werden k�nnen.
     */
    protected UnlockActionListener unlockActionListener = new UnlockActionListener();

    protected class UnlockActionListener implements ActionListener
    {
      public ActionEvent actionEvent = null;

      public void actionPerformed(ActionEvent arg0)
      {
        actionEvent = arg0;
        setUnlock();
      }
    }

    /**
     * Dieser DispatchResultListener kann der Methode
     * XNotifyableDispatch.dispatchWithNotification �bergeben werden und sorgt
     * in Verbindung mit den Methoden setLock() und waitForUnlock() daf�r, dass
     * ein Dispatch quasi synchron ausgef�hrt werden kann.
     */
    protected UnlockDispatchResultListener unlockDispatchResultListener = new UnlockDispatchResultListener();

    protected class UnlockDispatchResultListener implements
        XDispatchResultListener
    {
      public DispatchResultEvent resultEvent = null;

      public void disposing(EventObject arg0)
      {
        setUnlock();
      }

      public void dispatchFinished(DispatchResultEvent arg0)
      {
        resultEvent = arg0;
        setUnlock();
      }
    }
  }

  /**
   * Stellt das WollMuxEvent event in die EventQueue des EventProcessors.
   * 
   * @param event
   */
  private static void handle(WollMuxEvent event)
  {
    EventProcessor.getInstance().addEvent(event);
  }

  // *******************************************************************************************

  /**
   * Erzeugt ein neues WollMuxEvent, das den Dialog AbsenderAuswaehlen startet.
   */
  public static void handleShowDialogAbsenderAuswaehlen()
  {
    handle(new OnShowDialogAbsenderAuswaehlen());
  }

  /**
   * Dieses Event wird vom WollMux-Service (...comp.WollMux) und aus dem
   * WollMuxEventHandler ausgel�st und sorgt daf�r, dass der Dialog
   * AbsenderAusw�hlen gestartet wird.
   * 
   * @author christoph.lutz
   */
  private static class OnShowDialogAbsenderAuswaehlen extends BasicEvent
  {
    protected void doit() throws WollMuxFehlerException
    {
      WollMuxSingleton mux = WollMuxSingleton.getInstance();

      ConfigThingy conf = mux.getWollmuxConf();

      // Konfiguration auslesen:
      ConfigThingy whoAmIconf;
      ConfigThingy PALconf;
      ConfigThingy ADBconf;
      try
      {
        whoAmIconf = requireLastSection(conf, "AbsenderAuswaehlen");
        PALconf = requireLastSection(conf, "PersoenlicheAbsenderliste");
        ADBconf = requireLastSection(conf, "AbsenderdatenBearbeiten");
      }
      catch (ConfigurationErrorException e)
      {
        throw new CantStartDialogException(e);
      }

      // Dialog modal starten:
      try
      {
        setLock();
        new AbsenderAuswaehlen(whoAmIconf, PALconf, ADBconf, mux
            .getDatasourceJoiner(), unlockActionListener);
        waitForUnlock();
      }
      catch (java.lang.Exception e)
      {
        throw new CantStartDialogException(e);
      }

      WollMuxEventHandler.handlePALChangedNotify();
    }
  }

  // *******************************************************************************************

  /**
   * Erzeugt ein neues WollMuxEvent, das den Dialog
   * PersoenlichtAbsenderListe-Verwalten startet.
   */
  public static void handleShowDialogPersoenlicheAbsenderliste()
  {
    handle(new OnShowDialogPersoenlicheAbsenderlisteVerwalten());
  }

  /**
   * Dieses Event wird vom WollMux-Service (...comp.WollMux) und aus dem
   * WollMuxEventHandler ausgel�st und sorgt daf�r, dass der Dialog
   * Pers�nlicheAbsendeliste-Verwalten gestartet wird.
   * 
   * @author christoph.lutz
   */
  private static class OnShowDialogPersoenlicheAbsenderlisteVerwalten extends
      BasicEvent
  {
    protected void doit() throws WollMuxFehlerException
    {
      WollMuxSingleton mux = WollMuxSingleton.getInstance();
      ConfigThingy conf = mux.getWollmuxConf();

      // Konfiguration auslesen:
      ConfigThingy PALconf;
      ConfigThingy ADBconf;
      try
      {
        PALconf = requireLastSection(conf, "PersoenlicheAbsenderliste");
        ADBconf = requireLastSection(conf, "AbsenderdatenBearbeiten");
      }
      catch (ConfigurationErrorException e)
      {
        throw new CantStartDialogException(e);
      }

      // Dialog modal starten:
      try
      {
        setLock();
        new PersoenlicheAbsenderlisteVerwalten(PALconf, ADBconf, mux
            .getDatasourceJoiner(), unlockActionListener);
        waitForUnlock();
      }
      catch (java.lang.Exception e)
      {
        throw new CantStartDialogException(e);
      }

      handlePALChangedNotify();
    }
  }

  // *******************************************************************************************

  /**
   * Erzeugt ein neues WollMuxEvent, das den Funktionsdialog dialogName aufruft
   * und die zur�ckgelieferten Werte in die entsprechenden FormField-Objekte des
   * Dokuments doc eintr�gt.
   * 
   * Dieses Event wird vom WollMux-Service (...comp.WollMux) und aus dem
   * WollMuxEventHandler ausgel�st.
   */
  public static void handleFunctionDialog(XTextDocument doc, String dialogName)
  {
    handle(new OnFunctionDialog(doc, dialogName));
  }

  private static class OnFunctionDialog extends BasicEvent
  {
    private XTextDocument doc;

    private String dialogName;

    private OnFunctionDialog(XTextDocument doc, String dialogName)
    {
      this.doc = doc;
      this.dialogName = dialogName;
    }

    protected void doit() throws WollMuxFehlerException
    {
      WollMuxSingleton mux = WollMuxSingleton.getInstance();

      // Dialog aus Funktionsdialog-Bibliothek holen:
      Dialog dialog = mux.getFunctionDialogs().get(dialogName);
      if (dialog == null)
        throw new WollMuxFehlerException("Funktionsdialog '"
                                         + dialogName
                                         + "' ist nicht definiert.");

      // Dialoginstanz erzeugen und modal anzeigen:
      Dialog dialogInst = null;
      try
      {
        dialogInst = dialog.instanceFor(new HashMap());

        setLock();
        dialogInst.show(unlockActionListener, mux.getGlobalFunctions(), mux
            .getFunctionDialogs());
        waitForUnlock();
      }
      catch (ConfigurationErrorException e)
      {
        throw new CantStartDialogException(e);
      }

      // Abbruch, wenn der Dialog nicht mit OK beendet wurde.
      String cmd = unlockActionListener.actionEvent.getActionCommand();
      if (!cmd.equalsIgnoreCase("select")) return;

      // Dem Dokument den Fokus geben, damit die �nderungen des Benutzers
      // transparent mit verfolgt werden k�nnen.
      try
      {
        UNO.XModel(doc).getCurrentController().getFrame().getContainerWindow()
            .setFocus();
      }
      catch (java.lang.Exception e)
      {
        // keine Gef�hrdung des Ablaufs falls das nicht klappt.
      }

      // Alle Werte die der Funktionsdialog sicher zur�ck liefert werden in
      // das Dokument �bernommen.
      HashMap idToFormFields = mux.getTextDocumentModel(doc)
          .getIDToFormFields();
      if (idToFormFields != null)
      {
        Collection schema = dialogInst.getSchema();
        Iterator iter = schema.iterator();
        while (iter.hasNext())
        {
          String id = (String) iter.next();
          if (idToFormFields.containsKey(id))
          {
            Vector formFields = (Vector) idToFormFields.get(id);
            String value = dialogInst.getData(id).toString();

            // Formularwerte der entsprechenden Formularfelder auf value
            // setzen.
            Iterator ffiter = formFields.iterator();
            while (ffiter.hasNext())
            {
              FormField formField = (FormField) ffiter.next();
              if (formField != null)
                formField.setValue(value, mux.getGlobalFunctions());
            }
          }
        }
      }
    }

    public boolean requires(Object o)
    {
      return UnoRuntime.areSame(doc, o);
    }

    public String toString()
    {
      return this.getClass().getSimpleName()
             + "(#"
             + doc.hashCode()
             + ", '"
             + dialogName
             + "')";
    }
  }

  // *******************************************************************************************

  /**
   * Erzeugt ein neues WollMuxEvent, das einen modalen Dialog anzeigt, der
   * wichtige Versionsinformationen �ber den WollMux, die Konfiguration und die
   * WollMuxBar (nur falls wollmuxBarVersion nicht der Leersting ist) enth�lt.
   * Anmerkung: das WollMux-Modul hat keine Ahnung, welche WollMuxBar verwendet
   * wird. Daher ist es m�glich, �ber den Parameter wollMuxBarVersion eine
   * Versionsnummer der WollMuxBar zu �bergeben, die im Dialog angezeigt wird,
   * falls wollMuxBarVersion nicht der Leerstring ist.
   * 
   * Dieses Event wird vom WollMux-Service (...comp.WollMux) ausgel�st, wenn die
   * WollMux-url "wollmux:about" aufgerufen wurde.
   */
  public static void handleAbout(String wollMuxBarVersion)
  {
    handle(new OnAbout(wollMuxBarVersion));
  }

  private static class OnAbout extends BasicEvent
  {
    private String wollMuxBarVersion;

    private OnAbout(String wollMuxBarVersion)
    {
      this.wollMuxBarVersion = wollMuxBarVersion;
    }

    protected void doit() throws WollMuxFehlerException
    {
      WollMuxSingleton mux = WollMuxSingleton.getInstance();

      String str = "WollMux " + mux.getBuildInfo();
      if (wollMuxBarVersion != null && !wollMuxBarVersion.equals(""))
        str += "\nWollMux-Leiste " + wollMuxBarVersion;

      str += "\n\nWollMux-Konfiguration: " + mux.getConfVersionInfo();
      str += "\n" + WollMuxFiles.getDEFAULT_CONTEXT().toExternalForm();

      showInfoModal("Info �ber Vorlagen und Formulare (WollMux)", str);
    }

    public String toString()
    {
      return this.getClass().getSimpleName() + "()";
    }
  }

  // *******************************************************************************************

  /**
   * Erzeugt ein neues WollMuxEvent, das den FormularMax4000 aufruft f�r das
   * Dokument doc.
   * 
   * Dieses Event wird vom WollMux-Service (...comp.WollMux) und aus dem
   * WollMuxEventHandler ausgel�st.
   */
  public static void handleFormularMax4000Show(XTextDocument doc)
  {
    handle(new OnFormularMax4000Show(doc));
  }

  private static class OnFormularMax4000Show extends BasicEvent
  {
    private final XTextDocument doc;

    private OnFormularMax4000Show(XTextDocument doc)
    {
      this.doc = doc;
    }

    protected void doit() throws WollMuxFehlerException
    {

      if (doc == null) return;

      final TextDocumentModel model = WollMuxSingleton.getInstance()
          .getTextDocumentModel(doc);

      // Bestehenden Max in den Vordergrund holen oder neuen Max erzeugen.
      FormularMax4000 max = model.getCurrentFormularMax4000();
      if (max != null)
      {
        max.toFront();
      }
      else
      {
        max = new FormularMax4000(model, new ActionListener()
        {
          public void actionPerformed(ActionEvent actionEvent)
          {
            if (actionEvent.getSource() instanceof FormularMax4000)
              WollMuxEventHandler.handleFormularMax4000Returned(model);
          }
        }, WollMuxSingleton.getInstance().getGlobalFunctions());
        model.setCurrentFormularMax4000(max);
      }
    }

    public boolean requires(Object o)
    {
      return UnoRuntime.areSame(doc, o);
    }

    public String toString()
    {
      return this.getClass().getSimpleName() + "(#" + doc.hashCode() + ")";
    }
  }

  // *******************************************************************************************

  /**
   * Erzeugt ein neues WollMuxEvent, das aufgerufen wird, wenn ein
   * FormularMax4000 beendet wird und die entsprechenden internen Referenzen
   * gel�scht werden k�nnen.
   * 
   * Dieses Event wird vom EventProcessor geworfen, wenn der FormularMax
   * zur�ckkehrt.
   */
  public static void handleFormularMax4000Returned(TextDocumentModel model)
  {
    handle(new OnFormularMax4000Returned(model));
  }

  private static class OnFormularMax4000Returned extends BasicEvent
  {
    private TextDocumentModel model;

    private OnFormularMax4000Returned(TextDocumentModel model)
    {
      this.model = model;
    }

    protected void doit() throws WollMuxFehlerException
    {
      model.setCurrentFormularMax4000(null);
    }

    public String toString()
    {
      return this.getClass().getSimpleName() + "(#" + model.hashCode() + ")";
    }
  }

  // *******************************************************************************************

  /**
   * Erzeugt ein neues WollMuxEvent, das Auskunft dar�ber gibt, dass ein
   * TextDokument geschlossen wurde und damit auch das TextDocumentModel
   * disposed werden soll.
   * 
   * Dieses Event wird ausgel�st, wenn ein TextDokument geschlossen wird.
   */
  public static void handleTextDocumentClosed(XTextDocument doc)
  {
    handle(new OnTextDocumentClosed(doc));
  }

  private static class OnTextDocumentClosed extends BasicEvent
  {
    private XTextDocument doc;

    private OnTextDocumentClosed(XTextDocument doc)
    {
      this.doc = doc;
    }

    protected void doit() throws WollMuxFehlerException
    {
      WollMuxSingleton mux = WollMuxSingleton.getInstance();

      if (mux.hasTextDocumentModel(doc))
      {
        TextDocumentModel model = mux.getTextDocumentModel(doc);
        model.dispose();
      }
    }

    public String toString()
    {
      return this.getClass().getSimpleName() + "(#" + doc.hashCode() + ")";
    }
  }

  // *******************************************************************************************

  /**
   * Erzeugt ein neues WollMuxEvent, das die eigentliche Dokumentbearbeitung
   * eines TextDokuments startet.
   * 
   * @param xTextDoc
   *          Das XTextDocument, das durch den WollMux verarbeitet werden soll.
   */
  public static void handleProcessTextDocument(XTextDocument xTextDoc)
  {
    handle(new OnProcessTextDocument(xTextDoc));
  }

  /**
   * Dieses Event wird immer dann ausgel�st, wenn der GlobalEventBroadcaster von
   * OOo ein ON_NEW oder ein ON_LOAD-Event wirft. Das Event sorgt daf�r, dass
   * die eigentliche Dokumentbearbeitung durch den WollMux angestossen wird.
   * 
   * @author christoph.lutz
   */
  private static class OnProcessTextDocument extends BasicEvent
  {
    XTextDocument xTextDoc;

    public OnProcessTextDocument(XTextDocument xTextDoc)
    {
      this.xTextDoc = xTextDoc;
    }

    protected void doit() throws WollMuxFehlerException
    {
      if (xTextDoc == null) return;

      WollMuxSingleton mux = WollMuxSingleton.getInstance();
      TextDocumentModel model = mux.getTextDocumentModel(xTextDoc);

      // Konfigurationsabschnitt Textdokument verarbeiten:
      ConfigThingy tds = new ConfigThingy("Textdokument");
      try
      {
        tds = mux.getWollmuxConf().query("Fenster").query("Textdokument")
            .getLastChild();
        model.setWindowViewSettings(tds);
      }
      catch (NodeNotFoundException e)
      {
      }

      // M�gliche Aktionen f�r das neu ge�ffnete Dokument:
      DocumentCommandInterpreter dci = new DocumentCommandInterpreter(model,
          mux);

      try
      {
        // Dokumentkommandos setType und setPrintFunction auswerten
        dci.scanDocumentSettings();
        model.evaluatePersistentData();

        // Bei Vorlagen: Ausf�hrung der Dokumentkommandos
        if (model.isTemplate()) dci.executeTemplateCommands();

        // Bei Formularen: Konfigurationsabschnitt Fenster/Formular verarbeiten
        if (model.isFormDocument() || model.hasFormDescriptor())
        {
          try
          {
            model.setDocumentZoom(mux.getWollmuxConf().query("Fenster").query(
                "Formular").getLastChild().query("ZOOM"));
          }
          catch (java.lang.Exception e)
          {
          }
        }

        // Bei Formularen: Ausf�hrung der Dokumentkommandos des Formularsystems
        if (model.isFormDocument() || model.hasFormDescriptor())
          dci.executeFormCommands();
      }
      catch (java.lang.Exception e)
      {
        throw new WollMuxFehlerException("Fehler bei der Dokumentbearbeitung.",
            e);
      }
    }

    public boolean requires(Object o)
    {
      return UnoRuntime.areSame(xTextDoc, o);
    }

    public String toString()
    {
      return this.getClass().getSimpleName() + "(#" + xTextDoc.hashCode() + ")";
    }
  }

  // *******************************************************************************************

  /**
   * Erzeugt ein neues WollMuxEvent, welches daf�r sorgt, dass ein Dokument
   * ge�ffnet wird.
   * 
   * @param fragIDs
   *          Ein Vector mit fragIDs, wobei das erste Element die FRAG_ID des zu
   *          �ffnenden Dokuments beinhalten muss. Weitere Elemente werden in
   *          eine Liste zusammengefasst und als Parameter f�r das
   *          Dokumentkommando insertContent verwendet.
   * @param asTemplate
   *          true, wenn das Dokument als "Unbenannt X" ge�ffnet werden soll
   *          (also im "Template-Modus") und false, wenn das Dokument zum
   *          Bearbeiten ge�ffnet werden soll.
   */
  public static void handleOpenDocument(Vector fragIDs, boolean asTemplate)
  {
    handle(new OnOpenDocument(fragIDs, asTemplate));
  }

  /**
   * Dieses Event wird gestartet, wenn der WollMux-Service (...comp.WollMux) das
   * Dispatch-Kommando wollmux:openTemplate bzw. wollmux:openDocument empf�ngt
   * und sort daf�r, dass das entsprechende Dokument ge�ffnet wird.
   * 
   * @author christoph.lutz
   */
  private static class OnOpenDocument extends BasicEvent
  {
    private boolean asTemplate;

    private Vector fragIDs;

    private OnOpenDocument(Vector fragIDs, boolean asTemplate)
    {
      this.fragIDs = fragIDs;
      this.asTemplate = asTemplate;
    }

    protected void doit() throws WollMuxFehlerException
    {
      WollMuxSingleton mux = WollMuxSingleton.getInstance();

      // das erste Argument ist das unmittelbar zu landende Textfragment und
      // wird nach urlStr aufgel�st. Alle weiteren Argumente (falls vorhanden)
      // werden nach argsUrlStr aufgel�st.
      String loadUrlStr = "";
      String[] fragUrls = new String[fragIDs.size() - 1];

      Iterator iter = fragIDs.iterator();
      for (int i = 0; iter.hasNext(); ++i)
      {
        String frag_id = (String) iter.next();

        // Fragment-URL holen und aufbereiten:
        String urlStr;
        try
        {
          urlStr = mux.getTextFragmentList().getURLByID(frag_id);
        }
        catch (java.lang.Exception e)
        {
          throw new WollMuxFehlerException(
              "Die URL zum Textfragment mit der FRAG_ID '"
                  + frag_id
                  + "' kann nicht bestimmt werden.", e);
        }
        URL url;
        try
        {
          url = new URL(mux.getDEFAULT_CONTEXT(), urlStr);
          urlStr = url.toExternalForm();
        }
        catch (MalformedURLException e)
        {
          throw new WollMuxFehlerException(
              "Die URL '"
                  + urlStr
                  + "' des Textfragments mit der FRAG_ID '"
                  + frag_id
                  + "' ist ung�ltig.", e);
        }

        // URL durch den URL-Transformer jagen
        urlStr = UNO.getParsedUNOUrl(urlStr).Complete;

        // Workaround f�r Fehler in insertDocumentFromURL: Pr�fen ob URL
        // aufgel�st werden kann, da sonst der insertDocumentFromURL einfriert.
        try
        {
          url = new URL(urlStr);
        }
        catch (MalformedURLException e)
        {
          // darf nicht auftreten, da url bereits oben gepr�ft wurde...
          Logger.error(e);
        }
        try
        {
          WollMuxSingleton.checkURL(url);
        }
        catch (IOException e)
        {
          Logger.error(e);
          throw new WollMuxFehlerException(
              "Fehler beim Laden des Fragments mit der FRAG_ID '"
                  + frag_id
                  + "' von der URL '"
                  + url.toExternalForm()
                  + "'\n", e);
        }

        // URL in die in loadUrlStr (zum sofort �ffnen) und in argsUrlStr (zum
        // sp�ter �ffnen) aufnehmen
        if (i == 0)
          loadUrlStr = urlStr;
        else
          fragUrls[i - 1] = urlStr;
      }

      // open document as Template (or as document):
      try
      {
        XComponent doc = UNO.loadComponentFromURL(loadUrlStr, asTemplate, true);

        if (UNO.XTextDocument(doc) != null)
        {
          TextDocumentModel model = mux.getTextDocumentModel(UNO
              .XTextDocument(doc));
          model.setFragUrls(fragUrls);
        }
      }
      catch (java.lang.Exception x)
      {
        throw new WollMuxFehlerException("Die Vorlage mit der URL '"
                                         + loadUrlStr
                                         + "' kann nicht ge�ffnet werden.", x);
      }
    }

    public String toString()
    {
      return this.getClass().getSimpleName()
             + "("
             + ((asTemplate) ? "asTemplate" : "asDocument")
             + ", "
             + fragIDs
             + ")";
    }
  }

  // *******************************************************************************************

  /**
   * Erzeugt ein neues WollMuxEvent, das daf�r sorgt, dass alle registrierten
   * XPALChangeEventListener geupdated werden.
   */
  public static void handlePALChangedNotify()
  {
    handle(new OnPALChangedNotify());
  }

  /**
   * Dieses Event wird immer dann erzeugt, wenn ein Dialog zur Bearbeitung der
   * PAL geschlossen wurde und immer dann wenn die PAL z.B. durch einen
   * wollmux:setSender-Befehl ge�ndert hat. Das Event sorgt daf�r, dass alle im
   * WollMuxSingleton registrierten XPALChangeListener geupdatet werden.
   * 
   * @author christoph.lutz
   */
  private static class OnPALChangedNotify extends BasicEvent
  {
    protected void doit()
    {
      WollMuxSingleton mux = WollMuxSingleton.getInstance();

      // registrierte PALChangeListener updaten
      Iterator i = mux.palChangeListenerIterator();
      while (i.hasNext())
      {
        Logger.debug2("OnPALChangedNotify: Update XPALChangeEventListener");
        EventObject eventObject = new EventObject();
        eventObject.Source = WollMuxSingleton.getInstance();
        try
        {
          ((XPALChangeEventListener) i.next()).updateContent(eventObject);
        }
        catch (java.lang.Exception x)
        {
          i.remove();
        }
      }

      // Cache und LOS auf Platte speichern.
      try
      {
        mux.getDatasourceJoiner().saveCacheAndLOS(
            WollMuxFiles.getLosCacheFile());
      }
      catch (IOException e)
      {
        Logger.error(e);
      }
    }
  }

  // *******************************************************************************************

  /**
   * Erzeugt ein neues WollMuxEvent zum setzen des aktuellen Absenders.
   * 
   * @param senderName
   *          Name des Senders in der Form "Nachname, Vorname (Rolle)" wie sie
   *          auch der PALProvider bereith�lt.
   * @param idx
   *          der zum Sender senderName passende index in der sortierten
   *          Senderliste - dient zur Konsistenz-Pr�fung, damit kein Sender
   *          gesetzt wird, wenn die PAL der setzenden Komponente nicht mit der
   *          PAL des WollMux �bereinstimmt.
   */
  public static void handleSetSender(String senderName, int idx)
  {
    handle(new OnSetSender(senderName, idx));
  }

  /**
   * Dieses Event wird ausgel�st, wenn im WollMux-Service die methode setSender
   * aufgerufen wird. Es sort daf�r, dass ein neuer Absender gesetzt wird.
   * 
   * @author christoph.lutz
   */
  private static class OnSetSender extends BasicEvent
  {
    private String senderName;

    private int idx;

    public OnSetSender(String senderName, int idx)
    {
      this.senderName = senderName;
      this.idx = idx;
    }

    protected void doit()
    {
      DJDatasetListElement[] pal = WollMuxSingleton.getInstance()
          .getSortedPALEntries();

      // nur den neuen Absender setzen, wenn index und sender �bereinstimmen,
      // d.h.
      // die Absenderliste der entfernten WollMuxBar konsistent war.
      if (idx >= 0
          && idx < pal.length
          && pal[idx].toString().equals(senderName))
      {
        pal[idx].getDataset().select();
      }
      else
      {
        Logger.error("Setzen des Senders '"
                     + senderName
                     + "' schlug fehl, da der index '"
                     + idx
                     + "' nicht mit der PAL �bereinstimmt (Inkosistenzen?)");
      }

      WollMuxEventHandler.handlePALChangedNotify();
    }

    public String toString()
    {
      return this.getClass().getSimpleName()
             + "("
             + senderName
             + ", "
             + idx
             + ")";
    }
  }

  // *******************************************************************************************

  /**
   * Erzeugt ein neues WollMuxEvent, das die Druckfunktion zum TextDokument doc
   * auf functionName setzt.
   * 
   * Kann derzeit �ber den XWollMux-Service aufgerufen werden und steht damit
   * als Teil der Komfortdruckfunktionen auch in externen Routinen (z.B.
   * Basic-Makros, ...) zur Verf�gung.
   * 
   * @param doc
   *          Das TextDokument, zu dem die Druckfunktion gesetzt werden soll.
   * @param functionName
   *          der Name der Druckfunktion. Der Name muss ein g�ltiger
   *          Funktionsbezeichner sein und in einem Abschnitt "Druckfunktionen"
   *          in der wollmux.conf definiert sein.
   */
  public static void handleSetPrintFunction(XTextDocument doc,
      String functionName)
  {
    handle(new OnSetPrintFunction(doc, functionName));
  }

  private static class OnSetPrintFunction extends BasicEvent
  {
    private String functionName;

    private XTextDocument doc;

    public OnSetPrintFunction(XTextDocument doc, String functionName)
    {
      this.functionName = functionName;
      this.doc = doc;
    }

    protected void doit()
    {
      TextDocumentModel model = WollMuxSingleton.getInstance()
          .getTextDocumentModel(doc);

      model.setPrintFunctionName(functionName);
    }

    public String toString()
    {
      return this.getClass().getSimpleName()
             + "(#"
             + doc.hashCode()
             + ", '"
             + functionName
             + "')";
    }
  }

  // *******************************************************************************************

  /**
   * Erzeugt ein neues WollMuxEvent, welches daf�r sorgt, dass alle
   * Formularfelder Dokument auf den neuen Wert gesetzt werden. Bei
   * Formularfeldern mit TRAFO-Funktion wird die Transformation entsprechend
   * durchgef�hrt.
   * 
   * @param idToFormValues
   *          Eine HashMap die unter dem Schl�ssel fieldID den Vektor aller
   *          FormFields mit der ID fieldID liefert.
   * @param fieldId
   *          Die ID der Formularfelder, deren Werte angepasst werden sollen.
   * @param newValue
   *          Der neue untransformierte Wert des Formularfeldes.
   * @param funcLib
   *          Die Funktionsbibliothek, die zur Gewinnung der Trafo-Funktion
   *          verwendet werden soll.
   */
  public static void handleFormValueChanged(TextDocumentModel doc,
      HashMap idToFormValues, String fieldId, String newValue,
      FunctionLibrary funcLib)
  {
    handle(new OnFormValueChanged(doc, idToFormValues, fieldId, newValue,
        funcLib));
  }

  /**
   * Dieses Event wird (derzeit) vom FormModelImpl ausgel�st, wenn in der
   * Formular-GUI der Wert des Formularfeldes fieldID ge�ndert wurde und sorgt
   * daf�r, dass die Wert�nderung auf alle betroffenen Formularfelder im
   * Dokument doc �bertragen werden.
   * 
   * @author christoph.lutz
   */
  private static class OnFormValueChanged extends BasicEvent
  {
    private HashMap idToFormValues;

    private String fieldId;

    private String newValue;

    private FunctionLibrary funcLib;

    private TextDocumentModel doc;

    public OnFormValueChanged(TextDocumentModel doc, HashMap idToFormValues,
        String fieldId, String newValue, FunctionLibrary funcLib)
    {
      this.idToFormValues = idToFormValues;
      this.fieldId = fieldId;
      this.newValue = newValue;
      this.funcLib = funcLib;
      this.doc = doc;
    }

    protected void doit()
    {
      // Wenn es FormFields zu dieser id gibt, so werden alle FormFields auf den
      // neuen Stand gebracht.
      Vector formFields = (Vector) idToFormValues.get(fieldId);
      if (formFields != null)
      {
        Iterator i = formFields.iterator();
        while (i.hasNext())
        {
          FormField field = (FormField) i.next();
          try
          {
            field.setValue(newValue, funcLib);
          }
          catch (RuntimeException e)
          {
            // Absicherung gegen das manuelle L�schen von Dokumentinhalten.
          }
        }
      }

      // FormularDescriptor �ber die �nderung informieren. Dies ist vor allen
      // auch dazu notwendig, um Originalwerte zu sichern, zu denen es kein
      // FormField gibt.
      doc.setFormFieldValue(fieldId, newValue);
    }

    public String toString()
    {
      return this.getClass().getSimpleName()
             + "("
             + fieldId
             + "', '"
             + newValue
             + "')";
    }
  }

  // *******************************************************************************************

  /**
   * Erzeugt ein neues WollMuxEvent, welches daf�r sorgt, dass alle Textbereiche
   * im �bergebenen Dokument, die einer bestimmten Gruppe zugeh�ren ein- oder
   * ausgeblendet werden.
   * 
   * @param doc
   *          Das Dokument, welches die Textbereiche, die �ber Dokumentkommandos
   *          spezifiziert sind enth�lt.
   * @param invisibleGroups
   *          Enth�lt ein HashSet, das die groupId's aller als unsichtbar
   *          markierten Gruppen enth�lt.
   * @param groupId
   *          Die GROUP (ID) der ein/auszublendenden Gruppe.
   * @param visible
   *          Der neue Sichtbarkeitsstatus (true=sichtbar, false=ausgeblendet)
   * @param cmdTree
   *          Die DocumentCommandTree-Struktur, die den Zustand der
   *          Sichtbarkeiten enth�lt.
   */
  public static void handleSetVisibleState(XTextDocument doc,
      DocumentCommandTree cmdTree, HashSet invisibleGroups, String groupId,
      boolean visible)
  {
    handle(new OnSetVisibleState(doc, cmdTree, invisibleGroups, groupId,
        visible));
  }

  /**
   * Dieses Event wird (derzeit) vom FormModelImpl ausgel�st, wenn in der
   * Formular-GUI bestimmte Text-Teile des �bergebenen Dokuments ein- oder
   * ausgeblendet werden sollen.
   * 
   * @author christoph.lutz
   */
  private static class OnSetVisibleState extends BasicEvent
  {
    private XTextDocument doc;

    private String groupId;

    private boolean visible;

    private DocumentCommandTree cmdTree;

    private HashSet invisibleGroups;

    public OnSetVisibleState(XTextDocument doc, DocumentCommandTree cmdTree,
        HashSet invisibleGroups, String groupId, boolean visible)
    {
      this.doc = doc;
      this.cmdTree = cmdTree;
      this.invisibleGroups = invisibleGroups;
      this.groupId = groupId;
      this.visible = visible;
    }

    protected void doit()
    {
      // invisibleGroups anpassen:
      if (visible)
        invisibleGroups.remove(groupId);
      else
        invisibleGroups.add(groupId);

      DocumentCommand firstChangedCmd = null;

      // Kommandobaum durchlaufen und alle betroffenen Elemente updaten:
      Iterator iter = cmdTree.depthFirstIterator(false);
      while (iter.hasNext())
      {
        DocumentCommand cmd = (DocumentCommand) iter.next();
        Set groups = cmd.getGroups();
        if (!groups.contains(groupId)) continue;

        // Visibility-Status neu bestimmen:
        boolean setVisible = true;
        Iterator i = groups.iterator();
        while (i.hasNext())
        {
          String groupId = (String) i.next();
          if (invisibleGroups.contains(groupId)) setVisible = false;
        }

        // Kommando merken, dessen Sichtbarkeitsstatus sich zuerst �ndert und
        // den focus (ViewCursor) auf den Start des Bereichs setzen. Da das
        // Setzen eines ViewCursors in einen unsichtbaren Bereich nicht
        // funktioniert, wird die Methode focusRangeStart zwei mal aufgerufen,
        // je nach dem, ob der Bereich vor oder nach dem Setzen des neuen
        // Sichtbarkeitsstatus sichtbar ist.
        if (setVisible != cmd.isVisible() && firstChangedCmd == null)
        {
          firstChangedCmd = cmd;
          if (firstChangedCmd.isVisible()) focusRangeStart(cmd);
        }

        // neuen Sichtbarkeitsstatus setzen:
        try
        {
          cmd.setVisible(setVisible);
        }
        catch (RuntimeException e)
        {
          // Absicherung gegen das manuelle L�schen von Dokumentinhalten
        }
      }

      // Den Cursor (nochmal) auf den Anfang des Bookmarks setzen, dessen
      // Sichtbarkeitsstatus sich zuerst ge�ndert hat (siehe Begr�ndung oben).
      if (firstChangedCmd != null && firstChangedCmd.isVisible())
        focusRangeStart(firstChangedCmd);
    }

    /**
     * Diese Methode setzt den ViewCursor auf den Anfang des Bookmarks des
     * Dokumentkommandos cmd.
     * 
     * @param cmd
     *          Das Dokumentkommando, auf dessen Start der ViewCursor gesetzt
     *          werden soll.
     */
    private void focusRangeStart(DocumentCommand cmd)
    {
      XTextRange range = cmd.getTextRange();
      if (range != null)
        try
        {
          XController controller = UNO.XModel(doc).getCurrentController();
          XTextCursor cursor = UNO.XTextViewCursorSupplier(controller)
              .getViewCursor();
          cursor.gotoRange(range.getStart(), false);
        }
        catch (java.lang.Exception e)
        {
        }
    }

    public String toString()
    {
      return this.getClass().getSimpleName()
             + "('"
             + groupId
             + "', "
             + visible
             + ")";
    }
  }

  // *******************************************************************************************

  /**
   * Erzeugt ein Event, das den ViewCursor des Dokuments auf das aktuell in der
   * Formular-GUI bearbeitete Formularfeld setzt.
   * 
   * @param idToFormValues
   *          Eine HashMap die unter dem Schl�ssel fieldID den Vektor aller
   *          FormFields mit der ID fieldID liefert.
   * @param fieldId
   *          die ID des Formularfeldes das den Fokus bekommen soll. Besitzen
   *          mehrere Formularfelder diese ID, so wird bevorzugt das erste
   *          Formularfeld aus dem Vektor genommen, das keine Trafo enth�lt.
   *          Ansonsten wird das erste Formularfeld im Vektor verwendet.
   */
  public static void handleFocusFormField(HashMap idToFormValues, String fieldId)
  {
    handle(new OnFocusFormField(idToFormValues, fieldId));
  }

  /**
   * Dieses Event wird (derzeit) vom FormModelImpl ausgel�st, wenn in der
   * Formular-GUI ein Formularfeld den Fokus bekommen hat und es sorgt daf�r,
   * dass der View-Cursor des Dokuments das entsprechende FormField im Dokument
   * anspringt.
   * 
   * @author christoph.lutz
   */
  private static class OnFocusFormField extends BasicEvent
  {
    private HashMap idToFormValues;

    private String fieldId;

    public OnFocusFormField(HashMap idToFormValues, String fieldId)
    {
      this.idToFormValues = idToFormValues;
      this.fieldId = fieldId;
    }

    protected void doit()
    {
      if (idToFormValues.containsKey(fieldId))
      {
        Vector formFields = (Vector) idToFormValues.get(fieldId);
        FormField field = preferUntransformedFormField(formFields);
        try
        {
          if (field != null) field.focus();
        }
        catch (RuntimeException e)
        {
          // Absicherung gegen das manuelle L�schen von Dokumentinhalten.
        }
      }
      else
      {
        Logger.debug(this
                     + ": Es existiert kein Formularfeld mit der ID '"
                     + fieldId
                     + "' in diesem Dokument");
      }
    }

    public String toString()
    {
      return this.getClass().getSimpleName() + "('" + fieldId + "')";
    }
  }

  // *******************************************************************************************

  /**
   * Erzeugt ein Event, das die Position und Gr��e des �bergebenen
   * Dokument-Fensters auf die vorgegebenen Werte setzt. AHTUNG: Die Ma�angaben
   * beziehen sich auf die linke obere Ecke des Fensterinhalts OHNE die
   * Titelzeile und die Fensterdekoration des Rahmens. Um die linke obere Ecke
   * des gesamten Fensters richtig zu setzen, m�ssen die Gr��enangaben des
   * Randes der Fensterdekoration und die H�he der Titelzeile VOR dem Aufruf der
   * Methode entsprechend eingerechnet werden.
   * 
   * @param model
   *          Das XModel-Interface des Dokuments dessen Position/Gr��e gesetzt
   *          werden soll.
   * @param docX
   *          Die linke obere Ecke des Fensterinhalts X-Koordinate der Position
   *          in Pixel, gez�hlt von links oben.
   * @param docY
   *          Die Y-Koordinate der Position in Pixel, gez�hlt von links oben.
   * @param docWidth
   *          Die Gr��e des Dokuments auf der X-Achse in Pixel
   * @param docHeight
   *          Die Gr��e des Dokuments auf der Y-Achse in Pixel. Auch hier wird
   *          die Titelzeile des Rahmens nicht beachtet und muss vorher
   *          entsprechend eingerechnet werden.
   */
  public static void handleSetWindowPosSize(TextDocumentModel model, int docX,
      int docY, int docWidth, int docHeight)
  {
    handle(new OnSetWindowPosSize(model, docX, docY, docWidth, docHeight));
  }

  /**
   * Dieses Event wird vom FormModelImpl ausgel�st, wenn die Formular-GUI die
   * Position und die Ausmasse des Dokuments ver�ndert. Ruft direkt
   * setWindowsPosSize der UNO-API auf.
   * 
   * @author christoph.lutz
   */
  private static class OnSetWindowPosSize extends BasicEvent
  {
    private TextDocumentModel model;

    private int docX, docY, docWidth, docHeight;

    public OnSetWindowPosSize(TextDocumentModel model, int docX, int docY,
        int docWidth, int docHeight)
    {
      this.model = model;
      this.docX = docX;
      this.docY = docY;
      this.docWidth = docWidth;
      this.docHeight = docHeight;
    }

    protected void doit()
    {
      model.setWindowPosSize(docX, docY, docWidth, docHeight);
    }

    public boolean requires(Object o)
    {
      return UnoRuntime.areSame(model.doc, o);
    }

    public String toString()
    {
      return this.getClass().getSimpleName()
             + "("
             + docX
             + ", "
             + docY
             + ", "
             + docWidth
             + ", "
             + docHeight
             + ")";
    }
  }

  // *******************************************************************************************

  /**
   * Erzeugt ein Event, das die Anzeige des �bergebenen Dokuments auf sichtbar
   * oder unsichtbar schaltet. Dabei wird direkt die entsprechende Funktion der
   * UNO-API verwendet.
   * 
   * @param model
   *          Das XModel interface des dokuments, welches sichtbar oder
   *          unsichtbar geschaltet werden soll.
   * @param visible
   *          true, wenn das Dokument sichtbar geschaltet werden soll und false,
   *          wenn das Dokument unsichtbar geschaltet werden soll.
   */
  public static void handleSetWindowVisible(TextDocumentModel model,
      boolean visible)
  {
    handle(new OnSetWindowVisible(model, visible));
  }

  /**
   * Dieses Event wird vom FormModelImpl ausgel�st, wenn die Formular-GUI das
   * bearbeitete Dokument sichtbar/unsichtbar schalten m�chte. Ruft direkt
   * setVisible der UNO-API auf.
   * 
   * @author christoph.lutz
   */
  private static class OnSetWindowVisible extends BasicEvent
  {
    private TextDocumentModel model;

    boolean visible;

    public OnSetWindowVisible(TextDocumentModel model, boolean visible)
    {
      this.model = model;
      this.visible = visible;
    }

    protected void doit()
    {
      model.setWindowVisible(visible);
    }

    public boolean requires(Object o)
    {
      return UnoRuntime.areSame(model.doc, o);
    }

    public String toString()
    {
      return this.getClass().getSimpleName() + "(" + visible + ")";
    }
  }

  // *******************************************************************************************

  /**
   * Erzeugt ein Event, das das �bergebene Dokument schlie�t.
   * 
   * @param doc
   *          Das zu schlie�ende XTextDocument.
   */
  public static void handleCloseTextDocument(TextDocumentModel model)
  {
    handle(new OnCloseTextDocument(model));
  }

  /**
   * Dieses Event wird vom FormModelImpl ausgel�st, wenn der Benutzer die
   * Formular-GUI schlie�t und damit auch das zugeh�rige TextDokument
   * geschlossen werden soll.
   * 
   * @author christoph.lutz
   */
  private static class OnCloseTextDocument extends BasicEvent
  {
    private TextDocumentModel model;

    public OnCloseTextDocument(TextDocumentModel model)
    {
      this.model = model;
    }

    protected void doit()
    {
      model.close();
    }

    public boolean requires(Object o)
    {
      return UnoRuntime.areSame(model.doc, o);
    }

    public String toString()
    {
      return this.getClass().getSimpleName() + "(#" + model.hashCode() + ")";
    }
  }

  // *******************************************************************************************

  /**
   * Erzeugt ein neues WollMuxEvent, das ggf. notwendige interaktive
   * Initialisierungen vornimmt. Derzeit wird vor allem die Konsitenz der
   * pers�nlichen Absenderliste gepr�ft und der AbsenderAusw�hlen Dialog
   * gestartet, falls die Liste leer ist.
   */
  public static void handleInitialize()
  {
    handle(new OnInitialize());
  }

  /**
   * Dieses Event wird als erstes WollMuxEvent bei der Initialisierung des
   * WollMux im WollMuxSingleton erzeugt und �bernimmt alle benutzersichtbaren
   * (interaktiven) Initialisierungen wie z.B. das Darstellen des
   * AbsenderAusw�hlen-Dialogs, falls die PAL leer ist.
   * 
   * @author christoph.lutz
   */
  private static class OnInitialize extends BasicEvent
  {
    protected void doit()
    {
      WollMuxSingleton mux = WollMuxSingleton.getInstance();

      DatasourceJoiner dsj = mux.getDatasourceJoiner();

      // falls es noch keine Datens�tze im LOS gibt.
      if (dsj.getLOS().size() == 0)
      {

        // Die initialen Daten aus den OOo UserProfileData holen:
        String vorname = getUserProfileData("givenname");
        String nachname = getUserProfileData("sn");
        Logger.debug2("Initialize mit Vorname=\""
                      + vorname
                      + "\" und Nachname=\""
                      + nachname
                      + "\"");

        // im DatasourceJoiner nach dem Benutzer suchen:
        QueryResults r = null;
        if (!vorname.equals("") && !nachname.equals("")) try
        {
          r = dsj.find("Vorname", vorname, "Nachname", nachname);
        }
        catch (TimeoutException e)
        {
          Logger.error(e);
        }

        // Auswertung der Suchergebnisse:
        if (r != null)
        {
          // alle matches werden in die PAL kopiert:
          Iterator i = r.iterator();
          while (i.hasNext())
          {
            ((DJDataset) i.next()).copy();
          }
        }

        // Absender Ausw�hlen Dialog starten:
        WollMuxEventHandler.handleShowDialogAbsenderAuswaehlen();
      }
      else
      {
        // Liste der nicht zuordnenbaren Datens�tze erstellen und ausgeben:
        String names = "";
        List l = dsj.getStatus().lostDatasets;
        if (l.size() > 0)
        {
          Iterator i = l.iterator();
          while (i.hasNext())
          {
            Dataset ds = (Dataset) i.next();
            try
            {
              names += "- " + ds.get("Nachname") + ", ";
              names += ds.get("Vorname") + " (";
              names += ds.get("Rolle") + ")\n";
            }
            catch (ColumnNotFoundException x)
            {
              Logger.error(x);
            }
          }
          String message = "Die folgenden Datens�tze konnten nicht "
                           + "aus der Datenbank aktualisiert werden:\n\n"
                           + names
                           + "\nWenn dieses Problem nicht tempor�rer "
                           + "Natur ist, sollten Sie diese Datens�tze aus "
                           + "ihrer Absenderliste l�schen und neu hinzuf�gen!";
          showInfoModal("WollMux-Info", message);
        }
      }
    }

    private static String getUserProfileData(String key)
    {
      try
      {
        UnoService confProvider = UnoService.createWithContext(
            "com.sun.star.configuration.ConfigurationProvider",
            WollMuxSingleton.getInstance().getXComponentContext());

        UnoService confView = confProvider.create(
            "com.sun.star.configuration.ConfigurationAccess",
            new UnoProps("nodepath", "/org.openoffice.UserProfile/Data")
                .getProps());
        return confView.xNameAccess().getByName(key).toString();
      }
      catch (Exception e)
      {
        return "";
      }
    }
  }

  // *******************************************************************************************

  /**
   * Erzeugt ein neues WollMuxEvent zum Registrieren des �bergebenen
   * XPALChangeEventListeners.
   * 
   * @param listener
   */
  public static void handleAddPALChangeEventListener(
      XPALChangeEventListener listener)
  {
    handle(new OnAddPALChangeEventListener(listener));
  }

  /**
   * Dieses Event wird ausgel�st, wenn sich ein externer PALChangeEventListener
   * beim WollMux-Service registriert. Es sorgt daf�r, dass der
   * PALChangeEventListener in die Liste der registrierten
   * PALChangeEventListener im WollMuxSingleton aufgenommen wird.
   * 
   * @author christoph.lutz
   */
  private static class OnAddPALChangeEventListener extends BasicEvent
  {
    private XPALChangeEventListener listener;

    public OnAddPALChangeEventListener(XPALChangeEventListener listener)
    {
      this.listener = listener;
    }

    protected void doit()
    {
      WollMuxSingleton.getInstance().addPALChangeEventListener(listener);

      WollMuxEventHandler.handlePALChangedNotify();
    }

    public boolean requires(Object o)
    {
      return UnoRuntime.areSame(listener, o);
    }

    public String toString()
    {
      return this.getClass().getSimpleName() + "(#" + listener.hashCode() + ")";
    }
  }

  // *******************************************************************************************

  /**
   * Erzeugt ein neues WollMuxEvent, das den �bergebenen XPALChangeEventListener
   * deregistriert.
   * 
   * @param listener
   *          der zu deregistrierende XPALChangeEventListener
   */
  public static void handleRemovePALChangeEventListener(
      XPALChangeEventListener listener)
  {
    handle(new OnRemovePALChangeEventListener(listener));
  }

  /**
   * Dieses Event wird vom WollMux-Service (...comp.WollMux) ausgel�st wenn sich
   * ein externe XPALChangeEventListener beim WollMux deregistriert. Der zu
   * entfernende XPALChangeEventListerner wird anschlie�end im WollMuxSingleton
   * aus der Liste der registrierten XPALChangeEventListener genommen.
   * 
   * @author christoph.lutz
   */
  private static class OnRemovePALChangeEventListener extends BasicEvent
  {
    private XPALChangeEventListener listener;

    public OnRemovePALChangeEventListener(XPALChangeEventListener listener)
    {
      this.listener = listener;
    }

    protected void doit()
    {
      WollMuxSingleton.getInstance().removePALChangeEventListener(listener);
    }

    public String toString()
    {
      return this.getClass().getSimpleName() + "(#" + listener.hashCode() + ")";
    }
  }

  // *******************************************************************************************

  /**
   * Erzeugt ein neues WollMuxEvent das signaisiert, dass der Drucken-Knopf in
   * OOo gedr�ckt wurde und eine evtl. definierte Komfortdruckfunktion
   * ausgef�hrt werden soll.
   * 
   * Das Event wird ausgel�st, wenn der registrierte WollMuxDispatchInterceptor
   * eines Dokuments eine entsprechende Nachricht bekommt.
   */
  public static void handlePrintButtonPressed(XTextDocument doc,
      String fallbackDispatchUrlStr)
  {
    handle(new OnPrintButtonPressed(doc, fallbackDispatchUrlStr));
  }

  private static class OnPrintButtonPressed extends BasicEvent
  {
    private XTextDocument doc;

    private String fallbackDispatchUrlStr;

    public OnPrintButtonPressed(XTextDocument doc, String fallbackDispatchUrlStr)
    {
      this.doc = doc;
      this.fallbackDispatchUrlStr = fallbackDispatchUrlStr;
    }

    protected void doit() throws WollMuxFehlerException
    {
      if (doc == null) return;

      PrintFunction printFunc = null;

      TextDocumentModel model = WollMuxSingleton.getInstance()
          .getTextDocumentModel(doc);

      String printFunctionName = model.getPrintFunctionName();
      if (printFunctionName != null && !printFunctionName.equals(""))
      {
        printFunc = WollMuxSingleton.getInstance().getGlobalPrintFunctions()
            .get(printFunctionName);

        if (printFunc != null)
        {
          XPrintModel pmod = WollMuxSingleton.getInstance()
              .getTextDocumentModel(doc).getPrintModel();

          printFunc.invoke(pmod);
        }
        else
        {
          Logger.error("Druckfunktion '"
                       + printFunctionName
                       + "' nicht definiert.");
        }
      }

      if (printFunc == null)
      {
        // Fallback zum Dispatch �ber die URL von fallbackDispatchUrlStr
        com.sun.star.util.URL url = UNO.getParsedUNOUrl(fallbackDispatchUrlStr);
        XDispatch disp = WollMuxSingleton.getDispatchForModel(
            UNO.XModel(doc),
            url);
        if (disp != null) disp.dispatch(url, new PropertyValue[] {});
      }
    }

    public String toString()
    {
      return this.getClass().getSimpleName() + "(#" + doc.hashCode() + ")";
    }
  }

  // *******************************************************************************************

  /**
   * Erzeugt ein neues WollMuxEvent, das signasisiert, dass das Dokument doc in
   * der Anzahl numberOfCopies ausgedruckt werden soll. Nach Beendigung des
   * Events soll der CallBack des �bergebenen ActionsListeners aufgerufen
   * werden.
   * 
   * Das Event dient als Hilfe f�r die Komfortdruckfunktionen und wird vom
   * XPrintModel aufgerufen und mit diesem synchronisiert.
   */
  public static void handlePrint(XTextDocument doc, short numberOfCopies,
      ActionListener listener)
  {
    handle(new OnPrint(doc, numberOfCopies, listener));
  }

  private static class OnPrint extends BasicEvent
  {
    private XTextDocument doc;

    private short numberOfCopies;

    private ActionListener listener;

    public OnPrint(XTextDocument doc, short numberOfCopies,
        ActionListener listener)
    {
      this.doc = doc;
      this.numberOfCopies = numberOfCopies;
      this.listener = listener;
    }

    protected void doit() throws WollMuxFehlerException
    {
      TextDocumentModel model = WollMuxSingleton.getInstance()
          .getTextDocumentModel(doc);
      try
      {
        model.print(numberOfCopies);
      }
      catch (PrintFailedException e)
      {
        errorMessage(e);
      }
      listener.actionPerformed(null);
    }

    public String toString()
    {
      return this.getClass().getSimpleName() + "(#" + doc.hashCode() + ")";
    }
  }

  // *******************************************************************************************

  /**
   * Erzeugt ein neues WollMuxEvent, das signasisiert, dass eine weitere Ziffer
   * der Sachleitenden Verf�gungen eingef�gt werden, bzw. eine bestehende Ziffer
   * gel�scht werden soll.
   * 
   * Das Event wird von WollMux.dispatch(...) geworfen, wenn Aufgrund eines
   * Drucks auf den Knopf der OOo-Symbolleiste ein "wollmux:ZifferEinfuegen"
   * dispatch erfolgte.
   */
  public static void handleButtonZifferEinfuegenPressed(XTextDocument doc)
  {
    handle(new OnZifferEinfuegen(doc));
  }

  private static class OnZifferEinfuegen extends BasicEvent
  {
    private XTextDocument doc;

    public OnZifferEinfuegen(XTextDocument doc)
    {
      this.doc = doc;
    }

    protected void doit() throws WollMuxFehlerException
    {
      XTextCursor viewCursor = WollMuxSingleton.getInstance()
          .getTextDocumentModel(doc).getViewCursor();
      SachleitendeVerfuegung.verfuegungspunktEinfuegen(doc, viewCursor);
    }

    public String toString()
    {
      return this.getClass().getSimpleName()
             + "(#"
             + doc.hashCode()
             + ", viewCursor)";
    }
  }

  // *******************************************************************************************

  /**
   * Erzeugt ein neues WollMuxEvent, das signasisiert, dass eine Abdruckzeile
   * der Sachleitenden Verf�gungen eingef�gt werden, bzw. eine bestehende
   * Abdruckzeile gel�scht werden soll.
   * 
   * Das Event wird von WollMux.dispatch(...) geworfen, wenn Aufgrund eines
   * Drucks auf den Knopf der OOo-Symbolleiste ein "wollmux:Abdruck" dispatch
   * erfolgte.
   */
  public static void handleButtonAbdruckPressed(XTextDocument doc)
  {
    handle(new OnAbdruck(doc));
  }

  private static class OnAbdruck extends BasicEvent
  {
    private XTextDocument doc;

    public OnAbdruck(XTextDocument doc)
    {
      this.doc = doc;
    }

    protected void doit() throws WollMuxFehlerException
    {
      XTextCursor viewCursor = WollMuxSingleton.getInstance()
          .getTextDocumentModel(doc).getViewCursor();
      SachleitendeVerfuegung.abdruck(doc, viewCursor);
    }

    public String toString()
    {
      return this.getClass().getSimpleName()
             + "(#"
             + doc.hashCode()
             + ", viewCursor)";
    }
  }

  // *******************************************************************************************

  /**
   * Erzeugt ein neues WollMuxEvent, das signasisiert, dass eine Zuleitungszeile
   * der Sachleitenden Verf�gungen eingef�gt werden, bzw. eine bestehende
   * Zuleitungszeile gel�scht werden soll.
   * 
   * Das Event wird von WollMux.dispatch(...) geworfen, wenn Aufgrund eines
   * Drucks auf den Knopf der OOo-Symbolleiste ein "wollmux:Zuleitungszeile"
   * dispatch erfolgte.
   */
  public static void handleButtonZuleitungszeilePressed(XTextDocument doc)
  {
    handle(new OnButtonZuleitungszeilePressed(doc));
  }

  private static class OnButtonZuleitungszeilePressed extends BasicEvent
  {
    private XTextDocument doc;

    public OnButtonZuleitungszeilePressed(XTextDocument doc)
    {
      this.doc = doc;
    }

    protected void doit() throws WollMuxFehlerException
    {
      XTextCursor viewCursor = WollMuxSingleton.getInstance()
          .getTextDocumentModel(doc).getViewCursor();
      SachleitendeVerfuegung.zuleitungszeile(doc, viewCursor);
    }

    public String toString()
    {
      return this.getClass().getSimpleName()
             + "(#"
             + doc.hashCode()
             + ", viewCursor)";
    }
  }

  // *******************************************************************************************

  /**
   * Erzeugt ein neues WollMuxEvent, das daf�r sorgt, dass eine Datei
   * wollmux.dump erzeugt wird, die viele f�r die Fehlersuche relevanten
   * Informationen enth�lt wie z.B. Versionsinfo, Inhalt der wollmux.conf,
   * cache.conf, StringRepr�sentation der Konfiguration im Speicher und eine
   * Kopie der Log-Datei.
   * 
   * Das Event wird von der WollMuxBar geworfen, die (speziell f�r Admins, nicht
   * f�r Endbenutzer) einen entsprechenden Button besitzt.
   */
  public static void handleDumpInfo()
  {
    handle(new OnDumpInfo());
  }

  private static class OnDumpInfo extends BasicEvent
  {
    protected void doit() throws WollMuxFehlerException
    {
      WollMuxFiles.dumpInfo();
    }

    public String toString()
    {
      return this.getClass().getSimpleName() + "()";
    }
  }

  // *******************************************************************************************

  /**
   * Erzeugt ein neues WollMuxEvent, das signasisiert, dass das gesamte Office
   * (und damit auch der WollMux) OHNE Sicherheitsabfragen(!) beendet werden
   * soll.
   * 
   * Das Event wird von der WollMuxBar geworfen, die (speziell f�r Admins, nicht
   * f�r Endbenutzer) einen entsprechenden Button besitzt.
   */
  public static void handleKill()
  {
    handle(new OnKill());
  }

  private static class OnKill extends BasicEvent
  {
    protected void doit() throws WollMuxFehlerException
    {
      if (UNO.desktop != null)
      {
        UNO.desktop.terminate();
      }
      else
      {
        System.exit(0);
      }
    }

    public String toString()

    {
      return this.getClass().getSimpleName() + "()";
    }
  }

  // *******************************************************************************************

  /**
   * Erzeugt ein neues WollMuxEvent, das daf�r sorgt, dass bei Sachleitenden
   * Verf�gungen der Verf�gungspunkt mit der Nummer verfPunkt ausgedruck wird,
   * wobei alle darauffolgenden Verf�gungspunkte ausgeblendet sind.
   * 
   * Das Event wird �blicherweise von einer XPrintModel Implementierung (z.B.
   * TextDocument.PrintModel) geworfen, wenn aus der Druckfunktion die
   * zugeh�rige Methode aufgerufen wurde.
   * 
   * @param doc
   *          Das Dokument, welches die Verf�gungspunkte enth�lt.
   * @param verfPunkt
   *          Die Nummer des auszuduruckenden Verf�gungspunktes, wobei alle
   *          folgenden Verf�gungspunkte ausgeblendet werden.
   * @param numberOfCopies
   *          Die Anzahl der Ausfertigungen, in der verfPunkt ausgedruckt werden
   *          soll.
   * @param isDraft
   *          wenn isDraft==true, werden alle draftOnly-Bl�cke eingeblendet,
   *          ansonsten werden sie ausgeblendet.
   * @param isOriginal
   *          wenn isOriginal, wird die Ziffer des Verf�gungspunktes I
   *          ausgeblendet und alle notInOriginal-Bl�cke ebenso. Andernfalls
   *          sind Ziffer und notInOriginal-Bl�cke eingeblendet.
   * @param unlockActionListener
   *          Der unlockActionListener wird nach Beendigung des Druckens
   *          aufgerufen, wenn er != null.
   */
  public static void handlePrintVerfuegungspunkt(XTextDocument doc,
      short verfPunkt, short numberOfCopies, boolean isDraft,
      boolean isOriginal, ActionListener unlockActionListener)
  {
    handle(new OnPrintVerfuegungspunkt(doc, verfPunkt, numberOfCopies, isDraft,
        isOriginal, unlockActionListener));
  }

  private static class OnPrintVerfuegungspunkt extends BasicEvent
  {
    private XTextDocument doc;

    private short verfPunkt;

    private short numberOfCopies;

    private boolean isDraft;

    private boolean isOriginal;

    private ActionListener listener;

    public OnPrintVerfuegungspunkt(XTextDocument doc, short verfPunkt,
        short numberOfCopies, boolean isDraft, boolean isOriginal,
        ActionListener listener)
    {
      this.doc = doc;
      this.verfPunkt = verfPunkt;
      this.numberOfCopies = numberOfCopies;
      this.isDraft = isDraft;
      this.isOriginal = isOriginal;
      this.listener = listener;
    }

    protected void doit() throws WollMuxFehlerException
    {
      TextDocumentModel model = WollMuxSingleton.getInstance()
          .getTextDocumentModel(doc);

      try
      {
        SachleitendeVerfuegung.printVerfuegungspunkt(
            model,
            verfPunkt,
            numberOfCopies,
            isDraft,
            isOriginal);
      }
      catch (PrintFailedException e)
      {
        errorMessage(e);
      }
      catch (java.lang.Exception e)
      {
        Logger.error(e);
      }

      if (listener != null) listener.actionPerformed(null);
    }

    public String toString()
    {
      return this.getClass().getSimpleName()
             + "(#"
             + doc.hashCode()
             + ", verfPunkt="
             + verfPunkt
             + ", copies="
             + numberOfCopies
             + ", isDraft="
             + isDraft
             + ", isOriginal="
             + isOriginal
             + ")";
    }
  }

  // *******************************************************************************************

  /**
   * Erzeugt ein neues WollMuxEvent, das daf�r sorgt, dass der PrintSetupDialog
   * von OOo angezeigt wird, �ber den der aktuelle Drucker ausgew�hlt und
   * ge�ndert werden kann.
   * 
   * Das Event wird aus der Implementierung von XPrintModel (siehe
   * TextDocumentModel) geworfen, wenn die gleichnamige Methode dort aufgerufen
   * wird.
   * 
   * @param doc
   *          Das Dokument f�r das die Druckereinstellung gelten sollen.
   * @param onlyOnce
   *          Gibt an, dass der Dialog nur beim ersten Aufruf (aus Sicht eines
   *          Dokuments) der Methode angezeigt wird. Wurde bereits vor dem
   *          Aufruf ein PrintSetup-Dialog gestartet, so �ffnet sich der Dialog
   *          nicht und die Methode endet ohne Aktion.
   * @param unlockActionListener
   *          Der unlockActionListener wird immer aufgerufen, wenn sich doit()
   *          fertig ist.
   */
  public static void handleShowPrinterSetupDialog(XTextDocument doc,
      boolean onlyOnce, ActionListener unlockActionListener)
  {
    handle(new OnShowPrinterSetup(doc, onlyOnce, unlockActionListener));
  }

  private static class OnShowPrinterSetup extends BasicEvent
  {
    private XTextDocument doc;

    private boolean onlyOnce;

    private ActionListener listener;

    public OnShowPrinterSetup(XTextDocument doc, boolean onlyOnce,
        ActionListener listener)
    {
      this.doc = doc;
      this.onlyOnce = onlyOnce;
      this.listener = listener;
    }

    protected void doit() throws WollMuxFehlerException
    {
      TextDocumentModel model = WollMuxSingleton.getInstance()
          .getTextDocumentModel(doc);

      // dialog nicht anzeigen, wenn er bereits einmal angezeigt wurde und
      // onlyOnce gesetzt ist.
      if (model.printSettingsDone && onlyOnce)
      {
        if (listener != null) listener.actionPerformed(null);
        return;
      }

      // Dialog anzeigen:
      try
      {
        com.sun.star.util.URL url = UNO
            .getParsedUNOUrl(DispatchInterceptor.DISP_UNO_PRINTER_SETUP);
        XNotifyingDispatch disp = UNO.XNotifyingDispatch(WollMuxSingleton
            .getDispatchForModel(UNO.XModel(doc), url));

        if (disp != null)
        {
          setLock();
          disp.dispatchWithNotification(
              url,
              new PropertyValue[] {},
              unlockDispatchResultListener);
          waitForUnlock();
        }
      }
      catch (java.lang.Exception e)
      {
        Logger.error(e);
      }

      // Leider gibt der Dialog keine verwertbare Statusinformation zur�ck, die
      // beschreibt ob der Dialog mit "Abbrechen" oder mit "OK" beendet wurde.
      // Dann w�rde ich printSettingsDone nur dann auf true sezten, wenn der
      // Dialog mit OK beendet wurde...
      model.printSettingsDone = true;

      if (listener != null) listener.actionPerformed(null);
    }

    public String toString()
    {
      return this.getClass().getSimpleName()
             + "(#"
             + doc.hashCode()
             + ", onlyOnce="
             + onlyOnce
             + ")";
    }
  }

  // *******************************************************************************************
  // Globale Helper-Methoden

  /**
   * Diese Methode erzeugt einen modalen UNO-Dialog zur Anzeige von
   * Fehlermeldungen bei der Bearbeitung eines Events.
   * 
   * @param sTitle
   * @param sMessage
   */
  private static void showInfoModal(java.lang.String sTitle,
      java.lang.String sMessage)
  {
    try
    {
      XComponentContext m_xCmpCtx = WollMuxSingleton.getInstance()
          .getXComponentContext();

      // hole aktuelles Window:
      UnoService desktop = UnoService.createWithContext(
          "com.sun.star.frame.Desktop",
          m_xCmpCtx);

      // wenn ein Frame vorhanden ist, wird dieser als Parent f�r die Erzeugung
      // einer Infobox �ber das Toolkit verwendet, ansonsten wird ein
      // swing-Dialog gestartet.
      XFrame xFrame = desktop.xDesktop().getCurrentFrame();
      if (xFrame != null)
      {
        XWindow xParent = xFrame.getContainerWindow();

        // get access to the office toolkit environment
        com.sun.star.awt.XToolkit xKit = (com.sun.star.awt.XToolkit) UnoRuntime
            .queryInterface(com.sun.star.awt.XToolkit.class, m_xCmpCtx
                .getServiceManager().createInstanceWithContext(
                    "com.sun.star.awt.Toolkit",
                    m_xCmpCtx));

        // describe the info box ini it's parameters
        com.sun.star.awt.WindowDescriptor aDescriptor = new com.sun.star.awt.WindowDescriptor();
        aDescriptor.WindowServiceName = "infobox";
        aDescriptor.Bounds = new com.sun.star.awt.Rectangle(0, 0, 300, 200);
        aDescriptor.WindowAttributes = com.sun.star.awt.WindowAttribute.BORDER
                                       | com.sun.star.awt.WindowAttribute.MOVEABLE
                                       | com.sun.star.awt.WindowAttribute.CLOSEABLE;
        aDescriptor.Type = com.sun.star.awt.WindowClass.MODALTOP;
        aDescriptor.ParentIndex = 1;
        aDescriptor.Parent = (com.sun.star.awt.XWindowPeer) UnoRuntime
            .queryInterface(com.sun.star.awt.XWindowPeer.class, xParent);

        // create the info box window
        com.sun.star.awt.XWindowPeer xPeer = xKit.createWindow(aDescriptor);
        com.sun.star.awt.XMessageBox xInfoBox = (com.sun.star.awt.XMessageBox) UnoRuntime
            .queryInterface(com.sun.star.awt.XMessageBox.class, xPeer);
        if (xInfoBox == null) return;

        // fill it with all given informations and show it
        xInfoBox.setCaptionText("" + sTitle + "");
        xInfoBox.setMessageText("" + sMessage + "");
        xInfoBox.execute();
      }
      else
      {
        // zeige eine swing-infoBox an, falls kein OOo Parent vorhanden ist.

        // zu lange Strings umbrechen:
        final int MAXCHARS = 50;
        String formattedMessage = "";
        String[] lines = sMessage.split("\n");
        for (int i = 0; i < lines.length; i++)
        {
          String[] words = lines[i].split(" ");
          int chars = 0;
          for (int j = 0; j < words.length; j++)
          {
            String word = words[j];
            if (chars > 0 && chars + word.length() > MAXCHARS)
            {
              formattedMessage += "\n";
              chars = 0;
            }
            formattedMessage += word + " ";
            chars += word.length() + 1;
          }
          if (i != lines.length - 1) formattedMessage += "\n";
        }

        // infobox ausgeben:
        Common.setLookAndFeelOnce();
        javax.swing.JOptionPane.showMessageDialog(
            null,
            formattedMessage,
            sTitle,
            javax.swing.JOptionPane.INFORMATION_MESSAGE);
      }
    }
    catch (Exception e)
    {
      Logger.error(e);
    }
  }

  private static ConfigThingy requireLastSection(ConfigThingy cf,
      String sectionName) throws ConfigurationErrorException
  {
    try
    {
      return cf.query(sectionName).getLastChild();
    }
    catch (NodeNotFoundException e)
    {
      throw new ConfigurationErrorException(
          "Der Schl�ssel '"
              + sectionName
              + "' fehlt in der Konfigurationsdatei.", e);
    }
  }
}
