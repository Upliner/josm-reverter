package reverter;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.tools.Shortcut;

public class ReverterAction extends JosmAction {

	public ReverterAction()
	{
		super(tr("History Reverter"),null,tr("History reverter"),
                Shortcut.registerShortcut("tool:reverter",
                		"Display history reverter dialog",
                		KeyEvent.VK_R, Shortcut.GROUP_EDIT, 
						Shortcut.SHIFT_DEFAULT),
				true);
	}
	public void actionPerformed(ActionEvent arg0) {
		// TODO Auto-generated method stub

	}

}
