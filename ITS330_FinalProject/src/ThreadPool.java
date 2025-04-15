import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * A simple thread pool API.
 * 
 * Tasks that wish to get run by the thread pool must implement the
 * java.lang.Runnable interface.
 */

public class ThreadPool
{
	// used final to ensure the Threads references
	// if those are re-assigned by other threads, it may cause bugs
	// WorkerThread are the threads that actually execute tasks
	private final List<WorkerThread> workers;
	private final LinkedList<Runnable> taskQueue;

	// used volatile to notify all other threads when the value is changed.
	private volatile boolean isShutdown;
	/**
	 * Create a default size thread pool.
 	 */
	public ThreadPool() {
		this(4);
    }
	
	
	/**
	 * Create a thread pool with a specified size.
	 * 
	 * @param int size The number of threads in the pool.
	 */
	public ThreadPool(int size) {
		taskQueue = new LinkedList<>();
		workers = new ArrayList<>();
		isShutdown = false;

		for(int i = 0; i < size; i++) {
			WorkerThread worker = new WorkerThread();
			worker.start();
			workers.add(worker);
		}
    }

	/**
	 * Add work to the queue.
	 */
	public void add(Runnable task) {
		synchronized (taskQueue) { // preventing modify taskQueue from multiple threads
			if(isShutdown) {
				throw new IllegalStateException("ThreadPool is shutting down. No more tasks.");
			}
			taskQueue.add(task);
			taskQueue.notify();
		}
	}
	
	 
	/**
	 * shut down the pool.
	 */
	public void shutdown() {
		isShutdown = true;

		// Wake up all threads, let them exit if waiting
		synchronized (taskQueue) {
			taskQueue.notifyAll();
		}

		//Interrupt all worker threads
		for(WorkerThread worker : workers) {
			worker.interrupt();
		}

	}

	public void resize(int newSize) {
		synchronized (taskQueue) {
			int currentSize = workers.size();

			if(newSize > currentSize) { // if newSize is bigger
				for(int i = 0; i < newSize - currentSize; i++) {
					WorkerThread worker = new WorkerThread();
					worker.start();
					workers.add(worker);
				}
			} else if(newSize < currentSize) { // if newSize is smaller
				for(int i = 0; i < currentSize - newSize; i++) {
					WorkerThread worker = workers.remove(workers.size() - 1);
					worker.interrupt();
				}
			}
			// if newSize is same, do nothing
		}
	}

	private class WorkerThread extends Thread {
		public void run() {
			while (true) {
				Runnable task;
				synchronized (taskQueue) {
					// Wait for a task if the task Queue is empty
					while(taskQueue.isEmpty() && !isShutdown) {
						try {
							taskQueue.wait();
						} catch (InterruptedException e) {
							if (isShutdown) {
								return;
							}
              Thread.currentThread().interrupt(); // Re-set interrupted status
              System.err.println("Worker thread interrupted unexpectedly.");
							e.getStackTrace();
						}
					}

					// if shutdown() and no more tasks, exit
					if(isShutdown && taskQueue.isEmpty()) {
						return;
					}

					// Retrieve the next task from the queue
					task = taskQueue.removeFirst();
				}

				// execute
				try {
					task.run();
				} catch (RuntimeException e) {
          System.err.println("Task execution failed:");
          e.printStackTrace(); //Changed to print stack trace for debugging
				}
			}
		}
	}
}
