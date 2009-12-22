package reverter;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.gui.progress.NullProgressMonitor;
import org.openstreetmap.josm.gui.progress.PleaseWaitProgressMonitor;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.io.OsmTransferException;
import org.openstreetmap.josm.tools.Shortcut;

@SuppressWarnings("serial")
public class RevertChangesetAction extends JosmAction {

	public RevertChangesetAction()
	{
		super(tr("Revert changeset"),null,tr("Revert changeset"),
                Shortcut.registerShortcut("tool:revert",
                		"Tool: Revert changeset",
                		KeyEvent.VK_T, Shortcut.GROUP_EDIT, 
						Shortcut.SHIFT_DEFAULT),
				true);
	}
//	private ObjectsHistoryDialog dlg = null;
	public void actionPerformed(ActionEvent arg0) {
		ChangesetIdQuery dlg = new ChangesetIdQuery();
		dlg.setVisible(true);
		System.out.println(tr("reverter: {0}",dlg.getValue()));
		if (dlg.getValue() != 1) return;
		int changesetId = dlg.ChangesetId();
		if (changesetId == 0) return;
		System.out.println("reverter: Reverting...");
		ProgressMonitor progressMonitor = new PleaseWaitProgressMonitor();
		try {
			progressMonitor.beginTask("Reverting...",2);
			ChangesetReverter rev = new ChangesetReverter(changesetId);
			rev.DownloadOSMChange(progressMonitor);
			progressMonitor.worked(1);
			rev.DownloadHistory(progressMonitor);
			progressMonitor.worked(1);
			Main.main.undoRedo.add(rev.getCommand());
		} catch (OsmTransferException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally
		{
			progressMonitor.finishTask();
		}
	}

}