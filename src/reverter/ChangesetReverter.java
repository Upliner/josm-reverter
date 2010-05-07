package reverter;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.DeleteCommand;
import org.openstreetmap.josm.data.osm.Changeset;
import org.openstreetmap.josm.data.osm.ChangesetDataSet;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.PrimitiveId;
import org.openstreetmap.josm.data.osm.SimplePrimitiveId;
import org.openstreetmap.josm.data.osm.ChangesetDataSet.ChangesetDataSetEntry;
import org.openstreetmap.josm.data.osm.ChangesetDataSet.ChangesetModificationType;
import org.openstreetmap.josm.data.osm.history.HistoryOsmPrimitive;
import org.openstreetmap.josm.gui.progress.NullProgressMonitor;
import org.openstreetmap.josm.io.OsmServerChangesetReader;
import org.openstreetmap.josm.io.OsmTransferException;

public class ChangesetReverter {
    private int changesetId;
    private Changeset changeset;
    private ChangesetDataSet osmchange;
	static class MyCsDataset
	{
	    public HashMap<PrimitiveId,Integer> created = new HashMap<PrimitiveId,Integer>();
        public HashMap<PrimitiveId,Integer> updated = new HashMap<PrimitiveId,Integer>();
        public HashMap<PrimitiveId,Integer> deleted = new HashMap<PrimitiveId,Integer>();

        private static void put(HashMap<PrimitiveId,Integer> map,PrimitiveId id,int version)
        {
            if (map.containsKey(id)) {
                if (version < map.get(id))
                    map.put(id, version);
            } else {
                map.put(id, version);
            }
        }
        
        private void addEntry(ChangesetDataSetEntry entry)
        {
            HistoryOsmPrimitive t = entry.getPrimitive();
            if (entry.getModificationType() == ChangesetModificationType.CREATED) {
                put(created, new SimplePrimitiveId(t.getId(),t.getType()), (int)t.getVersion());
            } else if (entry.getModificationType() == ChangesetModificationType.UPDATED) {
                put(updated, new SimplePrimitiveId(t.getId(),t.getType()), (int)t.getVersion());
            } else if (entry.getModificationType() == ChangesetModificationType.DELETED) {
                put(deleted, new SimplePrimitiveId(t.getId(),t.getType()), (int)t.getVersion());
            } else throw new AssertionError();
        }
        
        public MyCsDataset(ChangesetDataSet ds)
        {
            Iterator<ChangesetDataSetEntry> iterator = ds.iterator();
            while (iterator.hasNext()) {
                addEntry(iterator.next());
            }
        }
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
    LinkedList<Command> cmds;

	public void RevertChangeset() throws OsmTransferException
	{
	    final DataSet ds = Main.main.getCurrentDataSet();
	    final MyCsDataset cds = new MyCsDataset(osmchange);
	    final OsmServerMultiObjectReader rdr = new OsmServerMultiObjectReader();
	    for (Map.Entry<PrimitiveId,Integer> entry : cds.updated.entrySet()) {
	        rdr.ReadObject(entry.getKey().getUniqueId(), entry.getValue()-1, entry.getKey().getType(),
	                NullProgressMonitor.INSTANCE);
	    }
        for (Map.Entry<PrimitiveId,Integer> entry : cds.deleted.entrySet()) {
            rdr.ReadObject(entry.getKey().getUniqueId(), entry.getValue()-1, entry.getKey().getType(),
                    NullProgressMonitor.INSTANCE);
        }
        final DataSet nds = rdr.parseOsm(NullProgressMonitor.INSTANCE);
        this.cmds = new DataSetToCmd(nds,ds).getCommandList();
        Set<PrimitiveId> keyset = cds.created.keySet();
        
        // Insert delete commands: first relations, then ways, then nodes
        for (PrimitiveId id : keyset) {
            if (id.getType() == OsmPrimitiveType.RELATION) {
                OsmPrimitive p = ds.getPrimitiveById(id);
                cmds.add(new DeleteCommand(p));
            }
        }
        for (PrimitiveId id : keyset) {
            if (id.getType() == OsmPrimitiveType.WAY) {
                OsmPrimitive p = ds.getPrimitiveById(id);
                if (p != null) cmds.add(new DeleteCommand(p));
            }
        }
        for (PrimitiveId id : keyset) {
            if (id.getType() == OsmPrimitiveType.NODE) {
                OsmPrimitive p = ds.getPrimitiveById(id);
                if (p != null) cmds.add(new DeleteCommand(p));
            }
        }
        
	}
	public List<Command> getCommands()
	{
		return cmds;//new SequenceCommand(tr("Revert changeset #{0}",changesetId),cmds);
	}
}
