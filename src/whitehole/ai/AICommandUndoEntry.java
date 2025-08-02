/*
 * Copyright (C) 2022 Whitehole Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package whitehole.ai;

import whitehole.editor.GalaxyEditorForm.IUndo;
import java.util.ArrayList;
import java.util.List;

/**
 * AICommandUndoEntry represents an undo entry for AI-generated commands.
 * It groups all changes from a single AI command into one undoable operation.
 */
public class AICommandUndoEntry implements IUndo {
    
    private final List<IUndo> undoEntries;
    private final String commandDescription;
    private final long timestamp;
    
    /**
     * Creates a new AI command undo entry from a list of transformations.
     * 
     * @param undoEntries List of individual undo entries to group together
     * @param commandDescription Description of the AI command that was executed
     */
    public AICommandUndoEntry(List<IUndo> undoEntries, String commandDescription) {
        this.undoEntries = new ArrayList<>(undoEntries);
        this.commandDescription = commandDescription != null ? commandDescription : "AI Command";
        this.timestamp = System.currentTimeMillis();
    }
    
    @Override
    public void performUndo() {
        // Perform undo operations in reverse order
        for (int i = undoEntries.size() - 1; i >= 0; i--) {
            undoEntries.get(i).performUndo();
        }
    }
    
    /**
     * Gets the description of the AI command.
     */
    public String getCommandDescription() {
        return commandDescription;
    }
    
    /**
     * Gets the timestamp when this command was executed.
     */
    public long getTimestamp() {
        return timestamp;
    }
    
    /**
     * Gets the number of individual operations in this AI command.
     */
    public int getOperationCount() {
        return undoEntries.size();
    }
    
    /**
     * Returns a string representation of this undo entry.
     */
    @Override
    public String toString() {
        return String.format("AI Command: %s (%d operations)", 
                           commandDescription, getOperationCount());
    }
}