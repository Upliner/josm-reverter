package reverter;

//import static org.openstreetmap.josm.tools.I18n.tr;
import static org.openstreetmap.josm.tools.I18n.marktr;

import java.awt.event.KeyEvent;

import javax.swing.JMenu;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.gui.MainMenu;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.plugins.Plugin;


public class ReverterPlugin extends Plugin {
	public ReverterPlugin()
	{
		JMenu historyMenu = Main.main.menu.addMenu(marktr("History"), KeyEvent.VK_H, Main.main.menu.defaultMenuPos);
		MainMenu.add(historyMenu, new ReverterAction());	   

	}
    public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame)
    {
    }
	
}
