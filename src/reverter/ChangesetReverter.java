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
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.history.HistoryDataSet;
import org.openstreetmap.josm.data.osm.history.HistoryOsmPrimitive;
import org.openstreetmap.josm.gui.progress.NullProgressMonitor;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.io.OsmServerChangesetReader;
import org.openstreetmap.josm.io.OsmTransferException;
import org.xml.sax.SAXException;

public class ChangesetReverter {
	OsmChange osmchange;
	
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
			changeset = new OsmServerChangesetReader().readChangeset(changesetId, null);
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
		
		osmchange = new OsmServerMultiObjectReader().parseOsmChange(changesetId, NullProgressMonitor.INSTANCE);
		DataSet ds;
		HistoryDataSet hds;
		ds = Main.main.getCurrentDataSet();
		hds = osmchange.data;
		for (HistoryOsmPrimitive hp : osmchange.create)
		{
			OsmPrimitive p = ds.getPrimitiveById(hp.getId(), hp.getType());
			if (p == null)
			{
				Warning(tr("Warning: {0} {1} wasn't found",hp.getType(),hp.getId()));
				missingList.add(hp);
				continue;
			}
			if (hds.getHistory(hp.getId(),hp.getType()).getLatest().getVersion() != p.getVersion())
				Warning(tr("Warning: version conflict in {0} id:{1} vesions: {2} and {3}",hp.getType(),hp.getId(),hds.getHistory(hp.getId(),hp.getType()).getLatest().getVersion(),p.getVersion()));
			deleteList.add(p);
		}
		for (HistoryOsmPrimitive hp : osmchange.modify)
		{
			OsmPrimitive p = ds.getPrimitiveById(hp.getId(), hp.getType());
			if (p == null)
			{
				Warning(tr("Warning: {0} {1} wasn't found",hp.getType(),hp.getId()));
				missingList.add(hp);
				continue;
			}
			if (hds.getHistory(hp.getId(),hp.getType()).getLatest().getVersion() != p.getVersion())
				Warning(tr("Warning: version conflict in {0} id:{1} vesions: {2} and {3}",hp.getType(),hp.getId(),hds.getHistory(hp.getId(),hp.getType()).getLatest().getVersion(),p.getVersion()));
			modifyList.put(p.getId(),new ModifyPair(p));
		}
	}
	public void DownloadHistory(ProgressMonitor progressMonitor) throws OsmTransferException
	{
		OsmServerMultiObjectReader rdr = new OsmServerMultiObjectReader();
		for (HistoryOsmPrimitive p : osmchange.delete)
			rdr.ReadObject(p.getId(), (int)p.getVersion()-1, p.getType(), NullProgressMonitor.INSTANCE);
		HistoryDataSet hds = osmchange.data;
		DataSet ds = rdr.parseOsm(NullProgressMonitor.INSTANCE);
		for (OsmPrimitive p : ds.allPrimitives())
		{
			p.setOsmId(p.getId(), (int)hds.getHistory(p.getId(),p.getType()).getLatest().getVersion());
  		    createList.add(p);
		}
		rdr = new OsmServerMultiObjectReader();
		for (HistoryOsmPrimitive p : osmchange.modify)
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
		for (ModifyPair p : modifyList.values())
			cmds.add(new ChangeCommand(p.current,p.reverted));
		for (OsmPrimitive p : deleteList)
			cmds.add(new DeleteCommand(p));
		return new SequenceCommand(tr("Revert changeset #{0}",changesetId),cmds);
	}
}
