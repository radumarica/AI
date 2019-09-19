package aim.actions;

import aim.Node;

import java.util.List;
/**
 * Action - expandable, can be decomposed
 */
public abstract class ExpandableAction extends Action {
    public abstract List<Action> decompose(Node node);
    protected List<ActionType> canBeDecomposedTo;

    public List<ActionType> canBeDecomposedTo() {
        return canBeDecomposedTo;
    }

}
