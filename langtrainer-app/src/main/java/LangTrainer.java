import com.igormaznitsa.langtrainer.engine.LangTrainerApplication;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

public final class LangTrainer {

  private LangTrainer() {
  }

  public static void main(final String[] args) {
    System.setProperty("javax.xml.parsers.SAXParserFactory",
        "org.apache.xerces.jaxp.SAXParserFactoryImpl");
    try {
      UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
    } catch (ClassNotFoundException
             | InstantiationException
             | IllegalAccessException
             | UnsupportedLookAndFeelException ex) {
      throw new IllegalStateException("Nimbus look and feel is not available", ex);
    }
    SwingUtilities.invokeLater(() -> new LangTrainerApplication().start());
  }
}
