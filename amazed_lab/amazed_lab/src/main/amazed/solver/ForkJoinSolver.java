package amazed.solver;

import amazed.maze.Maze;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * <code>ForkJoinSolver</code> implements a solver for
 * <code>Maze</code> objects using a fork/join multi-thread
 * depth-first search.
 * <p>
 * Instances of <code>ForkJoinSolver</code> should be run by a
 * <code>ForkJoinPool</code> object.
 */
public class ForkJoinSolver extends SequentialSolver {

    private volatile static boolean found = false;
    private int player;

    /**
     * Creates a solver that searches in <code>maze</code> from the
     * start node to a goal.
     *
     * @param maze   the maze to be searched
     */
    public ForkJoinSolver(Maze maze) {
        super(maze);
        this.player = maze.newPlayer(start);
        this.visited = new ConcurrentSkipListSet<>();
    }

    /**
     * Creates a solver that searches in <code>maze</code> from the
     * start node to a goal, forking after a given number of visited
     * nodes.
     *
     * @param maze        the maze to be searched
     * @param forkAfter   the number of steps (visited nodes) after
     *                    which a parallel task is forked; if
     *                    <code>forkAfter &lt;= 0</code> the solver never
     *                    forks new tasks
     */
    public ForkJoinSolver(Maze maze, int forkAfter) {
        this(maze);
        this.forkAfter = forkAfter;
    }

    /**
     * Searches for and returns the path, as a list of node
     * identifiers, that goes from the start node to a goal node in
     * the maze. If such a path cannot be found (because there are no
     * goals, or all goals are unreacheable), the method returns
     * <code>null</code>.
     *
     * @return   the list of node identifiers from the start node to a
     *           goal node in the maze; <code>null</code> if such a path cannot
     *           be found.
     */
    @Override
    public List<Integer> compute() {
        return parallelSearch();
    }

    private List<Integer> waitForOtherSolvers(List<ForkJoinSolver> otherSolvers) {
        List<Integer> result = null;

        for (ForkJoinSolver solver : otherSolvers) {
            List<Integer> partialPath = solver.join();
            if (partialPath != null) result = partialPath;
        }
        return result;
    }
    
    private ForkJoinSolver(Maze maze, int player, int start, Set<Integer> visited) {
        super(maze);
        this.player = player;
        this.start = start;
        this.visited = visited;
    }
    
    private List<Integer> parallelSearch() {
        ArrayList<ForkJoinSolver> otherSolvers = new ArrayList<>();
        frontier.push(start);

        while (!frontier.isEmpty() && !found) {
            int currentNode = frontier.pop();

            if (!visited.add(currentNode)) {
                continue;
            }
            maze.move(player, currentNode);
            

            if (maze.hasGoal(currentNode)) {
                found = true;
                return pathFromTo(start, currentNode);
            }
            ArrayList<Integer> unvisitedNeighbors = new ArrayList<>();

            for (Integer nb : maze.neighbors(currentNode)) {
                if (!visited.contains(nb)) unvisitedNeighbors.add(nb);
            }
            for (int i = 0; i < unvisitedNeighbors.size(); i++) {
                int nb = unvisitedNeighbors.get(i);
                predecessor.put(nb, currentNode);

                if (i == 0) {
                    frontier.push(nb);
                } else {
                    int newPlayer = maze.newPlayer(nb);
                    ForkJoinSolver solver = new ForkJoinSolver(maze, newPlayer, nb, visited);
                    otherSolvers.add(solver);
                    solver.fork();
                }
            }
        }
        List<Integer> pathToGoal = waitForOtherSolvers(otherSolvers);

        if (pathToGoal != null) {
            int mid = pathToGoal.remove(0);
            List<Integer> pathFromStart = pathFromTo(start, mid);
            pathFromStart.addAll(pathToGoal);
            return pathFromStart;
        }
        return null;
    }
}
