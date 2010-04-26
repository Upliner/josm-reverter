package reverter;

import static org.openstreetmap.josm.tools.I18n.tr;


import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedList;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.command.AddCommand;
import org.openstreetmap.josm.command.ChangeCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.DeleteCommand;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.osm.Changeset;
import org.openstreetmap.josm.data.osm.ChangesetDataSet;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.PrimitiveId;
import org.openstreetmap.josm.data.osm.ChangesetDataSet.ChangesetModificationType;
import org.openstreetmap.josm.data.osm.history.HistoryDataSet;
import org.openstreetmap.josm.data.osm.history.HistoryOsmPrimitive;
import org.openstreetmap.josm.gui.progress.NullProgressMonitor;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.io.MultiFetchServerObjectReader;
import org.openstreetmap.josm.io.OsmChangesetContentParser;
import org.openstreetmap.josm.io.OsmServerChangesetReader;
import org.openstreetmap.josm.io.OsmTransferException;
import org.xml.sax.SAXException;

public class ChangesetReverter {
    ChangesetDataSet osmchange;
	
	private Changeset changeset;
	private int changesetId;
	private void Warning(String str)
	{
		System.out.println(str);
	}
	public ChangesetReverter(int changesetId)
	{
		this.changesetId = changesetId;
		try {
		    OsmServerChangesetReader csr = new OsmServerChangesetReader();
			changeset = csr.readChangeset(changesetId, NullProgressMonitor.INSTANCE);
			osmchange = csr.downloadChangeset(changesetId, NullProgressMonitor.INSTANCE);
		} catch (OsmTransferException e) {
			e.printStackTrace();
		}
	}
	class ModifyPair
	{
		public OsmPrimitive current;
		public OsmPrimitive reverted;
		public ModifyPair(OsmPrimitive cur)
		{
			current=cur;
		}
	}

	//primitives from OSMChange
	LinkedList<OsmPrimitive> deleteList = new LinkedList<OsmPrimitive>();
	LinkedList<HistoryOsmPrimitive> missingList = new LinkedList<HistoryOsmPrimitive>();

	HashMap<Long,ModifyPair> modifyList = new HashMap<Long,ModifyPair>();
	//primitives from history
	LinkedList<OsmPrimitive> createList = new LinkedList<OsmPrimitive>();
	public void DownloadOSMChange(ProgressMonitor progressMonitor) throws OsmTransferException
	{
		
		DataSet ds;
		ds = Main.main.getCurrentDataSet();
		for (HistoryOsmPrimitive hp : osmchange.getPrimitivesByModificationType(ChangesetModificationType.CREATED))
		{
			OsmPrimitive p = ds.getPrimitiveById(hp.getId(), hp.getType());
			if (p == null)
			{
				Warning(tr("Warning: {0} {1} wasn't found",hp.getType(),hp.getId()));
				missingList.add(hp);
				continue;
			}
			if (hp.getVersion() != p.getVersion())
				Warning(tr("Warning: version conflict in {0} id:{1} vesions: {2} and {3}",hp.getType(),hp.getId(),hp.getVersion(),p.getVersion()));
			deleteList.add(p);
		}
		for (HistoryOsmPrimitive hp : osmchange.getPrimitivesByModificationType(ChangesetModificationType.UPDATED))
		{
			OsmPrimitive p = ds.getPrimitiveById(hp.getId(), hp.getType());
			if (p == null)
			{
				Warning(tr("Warning: {0} {1} wasn't found",hp.getType(),hp.getId()));
				missingList.add(hp);
				continue;
			}
            if (hp.getVersion() != p.getVersion())
                Warning(tr("Warning: version conflict in {0} id:{1} vesions: {2} and {3}",hp.getType(),hp.getId(),hp.getVersion(),p.getVersion()));
			modifyList.put(p.getId(),new ModifyPair(p));
		}
	}
	public void DownloadHistory(ProgressMonitor progressMonitor) throws OsmTransferException
	{
        OsmServerMultiObjectReader rdr = new OsmServerMultiObjectReader();
		for (HistoryOsmPrimitive p : osmchange.getPrimitivesByModificationType(ChangesetModificationType.DELETED))
			rdr.ReadObject(p.getId(), (int)p.getVersion()-1, p.getType(), NullProgressMonitor.INSTANCE);
		DataSet ds = rdr.parseOsm(NullProgressMonitor.INSTANCE);
		for (OsmPrimitive p : ds.allPrimitives())
		{
			p.setOsmId(p.getId(), (int)osmchange.getPrimitive(p).getVersion());
  		    createList.add(p);
		}
		rdr = new OsmServerMultiObjectReader();
		for (HistoryOsmPrimitive p : osmchange.getPrimitivesByModificationType(ChangesetModificationType.DELETED))
			rdr.ReadObject(p.getId(), (int)p.getVersion()-1, p.getType(), NullProgressMonitor.INSTANCE);
		ds = rdr.parseOsm(NullProgressMonitor.INSTANCE);
		for (OsmPrimitive p : ds.allPrimitives())
		{			
			ModifyPair mp = modifyList.get(p.getId());
			if (mp == null)
			{
				Warning(tr("reverter: Oops: {0} #{1} not found",p.getClass(),p.getId()));
			}
			mp.reverted = p;
			p.setOsmId(p.getId(), (int)mp.current.getVersion());
		}
	}
	public Command getCommand()
	{
		LinkedList<Command> cmds = new LinkedList<Command>();
		for (OsmPrimitive p : createList)
			cmds.add(new AddCommand(p));
		for (ModifyPair p : modifyList.values()) {
		    if (p.current.getType() == OsmPrimitiveType.NODE)
			    cmds.add(new ChangeCommand(p.current,p.reverted));
		}
        for (ModifyPair p : modifyList.values()) {
            if (p.current.getType() == OsmPrimitiveType.WAY)
                cmds.add(new ChangeCommand(p.current,p.reverted));
        }
        for (ModifyPair p : modifyList.values()) {
            if (p.current.getType() == OsmPrimitiveType.RELATION)
                cmds.add(new ChangeCommand(p.current,p.reverted));
        }
		for (OsmPrimitive p : deleteList)
			cmds.add(new DeleteCommand(p));
		return new SequenceCommand(tr("Revert changeset #{0}",changesetId),cmds);
	}
}
