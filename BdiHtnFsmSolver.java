package aimas.aiutils;

import aimas.Command;
import aimas.Node;
import aimas.PathFinder;
import aimas.actions.Action;
import aimas.actions.AtomicAction;
import aimas.actions.ExpandableAction;
import aimas.actions.expandable.AchieveGoalAction;
import aimas.actions.expandable.RemoveBoxAction;
import aimas.board.Cell;
import aimas.board.CoordinatesPair;
import aimas.board.entities.Agent;
import aimas.board.entities.Box;
import aimas.board.entities.Entity;

import java.util.*;

/**
 * State machine for building HTN tree and selecting intentions (actions) to work with
 */
public class BdiHtnFsmSolver {

    public static Map<CoordinatesPair, Double> cellWeights;

    // HTN tree related functions
    static void buildTree(Action action, Node node){
        if (action instanceof ExpandableAction) {
            ((ExpandableAction) action).decompose(node);
        }
        for (Action childAct: action.getChildrenActions()){
            buildTree(childAct, node);
        }
    }

    // pre-order traversal of HTN tree
    static void printTree(Action action){
        System.err.println(action.toString());
        for (Action childAct: action.getChildrenActions()){
            printTree(childAct);
        }
    }

    static Action findLeftMostDesc(Action action){
        Action tempAction = action;
        while (!tempAction.getChildrenActions().isEmpty()){
            tempAction = tempAction.getChildrenActions().get(0);
        }
        return tempAction;
    }

    static Action findNextHTNnode(Action action){ // find successor
        Action tempChild = action;
        Action tempParent = tempChild.getParent();
        if (tempParent == null){
            return action; // added for MA support, seems to be causing no issues in SA
        }
        Action tempNode;
        while (!tempParent.hasMoreChildren(tempChild.getNumberAsChild())){
            if (tempParent.getParent() == null) return action;
            tempNode = tempParent;
            tempChild = tempParent;
            tempParent = tempNode.getParent();
        }
        return tempParent.getChildOfNumber(tempChild.getNumberAsChild() + 1);
    }

    // state machine
    public static ArrayList<Command> HTNBDIFSM(Agent agent, Action htnroot, World world) {
        Action curAction = htnroot;
        Node curNode = world.getState();
        int curState = 1;
        int prevState = -2;
        boolean finished = false;
        Action curAchieveGoalAction = htnroot; // will 100% be overwritten
        ArrayList<Command> allCommands = new ArrayList<>();
        ArrayList<AchieveGoalAction> achievedGoals = new ArrayList<>();
        while(!finished) {
            if (curNode.isSolved()){
                finished = true;
                break;
            }
            System.err.println(curState + " " + curAction + "***" + curAction.getParent());
            switch (curState) {
                case 1: // checking nature of action
                    System.err.println(achievedGoals);
                    if (curAction instanceof AchieveGoalAction) curAchieveGoalAction = curAction;
                    if (curAction.isAchieved(curNode)) {
                        curState = 2;
                    }
                    else if (curAction instanceof ExpandableAction) {
                        curState = 3;
                    }
                    else if (curAction instanceof AtomicAction) {
                        curState = 4;
                    }
                    break;

                case 2: // achieved, find successor
                    Action successor = findNextHTNnode(curAction);

                    if (successor instanceof  AchieveGoalAction){ //cur goal achieved add to list
                        achievedGoals.add((AchieveGoalAction)curAchieveGoalAction);
                    }

                    if (successor.equals(curAction)) { // no more successors, finished
                        for (Action act : achievedGoals){
                            System.err.println(act +" "+ act.isAchieved(curNode));
                        }
                        System.err.println(curAction);
                        System.err.println("Solution found");
                        finished = true;

                    }
                    else { // proceeding
                        curAction = successor;
                        curState = 1;
                    }
                    break;

                case 3: // expandable, not achieved, decomposing
                    ((ExpandableAction)curAction).decompose(curNode);
                    try {
                        Action firstChild = curAction.getChildOfNumber(0);

                        if (firstChild instanceof ExpandableAction) {
                            curAction = firstChild;
                            curState = 1;
                        } else if (firstChild instanceof AtomicAction) {
                            curAction = firstChild;
                            curState = 4;
                        }
                        break;
                    }
                    catch(IndexOutOfBoundsException e){
                        System.err.println(curNode);
                        System.err.println("Faling action: " + curAction);
                    }

                case 4: // execute atomic
                    System.err.println("state 4 " + curAction);
                    ArrayList<Node> pathOfNodes = BestFirstSearch.AStar(curNode,(AtomicAction) curAction);
                    Collections.reverse(pathOfNodes);
                    boolean atomicActionDone = false;
                    System.err.println(curAction);
                    //System.err.println("size: " + pathOfNodes.size());
                    for (Node pathNode : pathOfNodes){
                        // System.err.println(pathNode);
                        if(world.isAValidMove(agent.getNumber(),pathNode.getAction())){
                            allCommands.add(pathNode.getAction());
                            world.makeAMove(agent.getNumber(), pathNode.getAction());
                            // System.err.println(world.getState());
                        }
                        else {
                            //  System.err.println("problem: " +pathNode.getAction().toString());
                            curState = 4;
                            break;
                        }
                        curNode = world.getState();
                        System.err.println(curNode);
                        atomicActionDone = true;
                    }

                    Iterator<AchieveGoalAction> iter = achievedGoals.iterator();
                    while (iter.hasNext()) {
                        AchieveGoalAction act = iter.next();
                        if (!act.isAchieved(curNode)){
                            AchieveGoalAction achieveAgain = new AchieveGoalAction(act.goalCell, act.box, agent, act.getParent());
                            boolean contained = false;
                            for (int i = act.getNumberAsChild(); i < act.getParent().getChildrenActions().size(); i++){
                                if (act.getParent().getChildOfNumber(i).equals(achieveAgain)) {
                                    contained = true;
                                    break;
                                }
                            }
                            if (!contained) {
                                curAchieveGoalAction.getParent().setChildOfNumber(achieveAgain,curAchieveGoalAction.getNumberAsChild()+1);
                                iter.remove();
                            }

                        }
                    }

                    if (atomicActionDone) {
                        if (curAchieveGoalAction.isAchieved(world.getState())){
                            curState = 1;
                        }
                        else {
                            curState = 5;
                        }
                    }
                    break;

                case 5:
                    if (curAction.isAchieved(curNode)) {
                        curState = 6;
                    }
                    else {
                        curAction = curAction.getParent();
                        curState = 1;
                    }
                    break;

                case 6:
                    if (curAction.getParent().isAchieved(curNode)){
                        curAction = curAction.getParent();
                        curState = 1;
                    }
                    else {
                        curState = 7;
                    }
                    break;

                case 7:
                    if (curAction.getParent().hasMoreChildren(curAction.getNumberAsChild())) {
                        curAction = curAction.getParent().getChildOfNumber(curAction.getNumberAsChild()+1);
                        curState = 1;
                    }
                    else {
                        curAction = curAction.getParent(); // repeating
                        curState = 1;
                    }
                    break;
            }
        }
        return allCommands;
    }

    public static CoordinatesPair findParkingCell(Node node, CoordinatesPair initialCell, RemoveBoxAction remBoxAct,
                                                  ArrayList<CoordinatesPair> blackList){

        Agent agent = remBoxAct.getAgent(); // agent removing box
        ArrayList<ArrayList<Cell>> level = node.getLevel();
        Map<CoordinatesPair, Double> subsetOfCellWeights = new HashMap<>();
        CoordinatesPair bestParkingCell = new CoordinatesPair(-5,-5);
        for (int i=0; i<level.size(); i++){
            for (int j = 0; j<level.get(i).size();j++){
                Entity prevEntity =  node.getCellAtCoords(level.get(i).get(j).getCoordinates()).getEntity();

                if (PathFinder.pathExists(level,initialCell,level.get(i).get(j).getCoordinates(),
                        true, false, true) &&
                        !(level.get(i).get(j).getEntity() instanceof Box)
                        && !blackList.contains(level.get(i).get(j).getCoordinates())) {
                    subsetOfCellWeights.put(level.get(i).get(j).getCoordinates(),
                            cellWeights.get(level.get(i).get(j).getCoordinates()));
                }
            }
        }
        if (!subsetOfCellWeights.isEmpty()) {
            bestParkingCell = findBestAmongReachable(subsetOfCellWeights,node,initialCell, agent);
        }
        else {
            bestParkingCell = findBestAmongReachable(cellWeights,node,initialCell, agent);
        }
        return bestParkingCell;
    }

    public static CoordinatesPair findBestAmongReachable(Map<CoordinatesPair, Double> subsetOfCellWeights, Node node,
                                                         CoordinatesPair initialCell, Agent agent){
        double min = 999.0;
        CoordinatesPair bestParkingCell = initialCell;

        System.err.println("how come");

        for (Map.Entry<CoordinatesPair, Double> entry : subsetOfCellWeights.entrySet()){
            Double curWeight = entry.getValue();
            if(entry.getKey().equals(agent.getCoordinates(node))) curWeight +=10;
            if (node.getCellAtCoords(entry.getKey()).isGoal()) {
                if (Character.toUpperCase(node.getCellAtCoords(entry.getKey()).getGoalLetter())
                        == ((Box)node.getCellAtCoords(initialCell).getEntity()).getLetter()){
                    curWeight -= 10;
                }
                else curWeight += 50;
            }
            if(curWeight.doubleValue()
                    +2*Action.manhDist(initialCell.getX(), initialCell.getY(), entry.getKey().getX(), entry.getKey().getY()) < min){
                if(!(node.getCellAtCoords(entry.getKey()).getEntity() instanceof Box)) {
                    min = curWeight.doubleValue()
                            +2*Action.manhDist(initialCell.getX(), initialCell.getY(),entry.getKey().getX(),entry.getKey().getY());
                    bestParkingCell = entry.getKey();
                }
            }
        }
        double p = 50; // punishment value to avoid parking in the same cell again
        cellWeights.put(bestParkingCell, (Double)(cellWeights.get(bestParkingCell).doubleValue()+p));
        return bestParkingCell;
    }
}