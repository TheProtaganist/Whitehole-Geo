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

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TimeoutHandler manages timeout scenarios for AI operations, providing user feedback
 * and cancellation options for slow responses.
 */
public class TimeoutHandler {
    
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int WARNING_TIMEOUT_SECONDS = 15;
    
    /**
     * Result of timeout handling.
     */
    public enum TimeoutAction {
        CONTINUE,    // Continue waiting
        CANCEL,      // Cancel the operation
        RETRY        // Retry the operation
    }
    
    /**
     * Monitors a Future task and shows timeout dialogs if needed.
     */
    public static class TimeoutMonitor {
        private final Future<?> task;
        private final Component parent;
        private final String operationName;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicBoolean completed = new AtomicBoolean(false);
        private Timer warningTimer;
        private Timer timeoutTimer;
        private JDialog timeoutDialog;
        
        public TimeoutMonitor(Future<?> task, Component parent, String operationName) {
            this.task = task;
            this.parent = parent;
            this.operationName = operationName;
            startMonitoring();
        }
        
        private void startMonitoring() {
            // Warning timer - shows a warning after 15 seconds
            warningTimer = new Timer(WARNING_TIMEOUT_SECONDS * 1000, e -> {
                if (!completed.get() && !cancelled.get()) {
                    showWarningDialog();
                }
            });
            warningTimer.setRepeats(false);
            warningTimer.start();
            
            // Timeout timer - shows timeout dialog after 30 seconds
            timeoutTimer = new Timer(DEFAULT_TIMEOUT_SECONDS * 1000, e -> {
                if (!completed.get() && !cancelled.get()) {
                    handleTimeout();
                }
            });
            timeoutTimer.setRepeats(false);
            timeoutTimer.start();
        }
        
        private void showWarningDialog() {
            SwingUtilities.invokeLater(() -> {
                if (completed.get() || cancelled.get()) return;
                
                String message = operationName + " is taking longer than expected.\n" +
                               "This may be due to high server load or complex processing.\n\n" +
                               "The operation is still running in the background.";
                
                JOptionPane.showMessageDialog(
                    parent,
                    message,
                    "Operation Taking Longer Than Expected",
                    JOptionPane.INFORMATION_MESSAGE
                );
            });
        }
        
        private void handleTimeout() {
            SwingUtilities.invokeLater(() -> {
                if (completed.get() || cancelled.get()) return;
                
                TimeoutAction action = showTimeoutDialog();
                
                switch (action) {
                    case CANCEL:
                        cancel();
                        break;
                    case CONTINUE:
                        // Extend timeout by another 30 seconds
                        extendTimeout();
                        break;
                    case RETRY:
                        cancel();
                        // Retry would be handled by the caller
                        break;
                }
            });
        }
        
        private TimeoutAction showTimeoutDialog() {
            String message = operationName + " has been running for " + DEFAULT_TIMEOUT_SECONDS + " seconds.\n\n" +
                           "Possible causes:\n" +
                                       "- High server load on the AI service\n" +
            "- Complex command requiring extensive processing\n" +
            "- Network connectivity issues\n\n" +
                           "What would you like to do?";
            
            String[] options = {"Continue Waiting", "Cancel Operation", "Cancel and Retry"};
            
            int choice = JOptionPane.showOptionDialog(
                parent,
                message,
                "Operation Timeout",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options,
                options[0]
            );
            
            switch (choice) {
                case 0: return TimeoutAction.CONTINUE;
                case 1: return TimeoutAction.CANCEL;
                case 2: return TimeoutAction.RETRY;
                default: return TimeoutAction.CANCEL;
            }
        }
        
        private void extendTimeout() {
            // Restart the timeout timer for another 30 seconds
            if (timeoutTimer != null) {
                timeoutTimer.stop();
            }
            
            timeoutTimer = new Timer(DEFAULT_TIMEOUT_SECONDS * 1000, e -> {
                if (!completed.get() && !cancelled.get()) {
                    handleTimeout();
                }
            });
            timeoutTimer.setRepeats(false);
            timeoutTimer.start();
        }
        
        public void cancel() {
            cancelled.set(true);
            if (task != null && !task.isDone()) {
                task.cancel(true);
            }
            cleanup();
        }
        
        public void markCompleted() {
            completed.set(true);
            cleanup();
        }
        
        public boolean isCancelled() {
            return cancelled.get();
        }
        
        public boolean isCompleted() {
            return completed.get();
        }
        
        private void cleanup() {
            if (warningTimer != null) {
                warningTimer.stop();
                warningTimer = null;
            }
            if (timeoutTimer != null) {
                timeoutTimer.stop();
                timeoutTimer = null;
            }
            if (timeoutDialog != null) {
                timeoutDialog.dispose();
                timeoutDialog = null;
            }
        }
    }
    
    /**
     * Creates a progress dialog that shows timeout information and cancellation options.
     */
    public static class TimeoutProgressDialog extends JDialog {
        private final Future<?> task;
        private final String operationName;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private JProgressBar progressBar;
        private JLabel statusLabel;
        private JLabel timeLabel;
        private JButton cancelButton;
        private Timer updateTimer;
        private long startTime;
        
        public TimeoutProgressDialog(Component parent, Future<?> task, String operationName) {
            super(SwingUtilities.getWindowAncestor(parent), operationName, ModalityType.APPLICATION_MODAL);
            this.task = task;
            this.operationName = operationName;
            this.startTime = System.currentTimeMillis();
            
            initializeComponents();
            startUpdateTimer();
            pack();
            setLocationRelativeTo(parent);
        }
        
        private void initializeComponents() {
            setLayout(new BorderLayout(10, 10));
            getRootPane().setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
            
            // Status panel
            JPanel statusPanel = new JPanel(new BorderLayout(5, 5));
            
            statusLabel = new JLabel("Processing " + operationName.toLowerCase() + "...");
            statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));
            
            timeLabel = new JLabel("Elapsed time: 0 seconds");
            timeLabel.setFont(timeLabel.getFont().deriveFont(Font.PLAIN, 11f));
            
            statusPanel.add(statusLabel, BorderLayout.NORTH);
            statusPanel.add(timeLabel, BorderLayout.SOUTH);
            
            // Progress bar
            progressBar = new JProgressBar();
            progressBar.setIndeterminate(true);
            progressBar.setStringPainted(true);
            progressBar.setString("Please wait...");
            
            // Button panel
            JPanel buttonPanel = new JPanel(new FlowLayout());
            cancelButton = new JButton("Cancel");
            cancelButton.addActionListener(e -> cancel());
            buttonPanel.add(cancelButton);
            
            add(statusPanel, BorderLayout.NORTH);
            add(progressBar, BorderLayout.CENTER);
            add(buttonPanel, BorderLayout.SOUTH);
        }
        
        private void startUpdateTimer() {
            updateTimer = new Timer(1000, e -> updateTimeDisplay());
            updateTimer.start();
        }
        
        private void updateTimeDisplay() {
            if (task.isDone() || cancelled.get()) {
                dispose();
                return;
            }
            
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
            timeLabel.setText("Elapsed time: " + elapsed + " seconds");
            
            // Update status based on elapsed time
            if (elapsed > WARNING_TIMEOUT_SECONDS) {
                statusLabel.setText("Still processing " + operationName.toLowerCase() + " (taking longer than expected)...");
                progressBar.setString("This may take a while due to server load...");
            }
            
            if (elapsed > DEFAULT_TIMEOUT_SECONDS) {
                statusLabel.setText("Processing is taking unusually long...");
                progressBar.setString("Consider cancelling if this continues...");
            }
        }
        
        public void cancel() {
            cancelled.set(true);
            if (task != null && !task.isDone()) {
                task.cancel(true);
            }
            dispose();
        }
        
        public boolean wasCancelled() {
            return cancelled.get();
        }
        
        @Override
        public void dispose() {
            if (updateTimer != null) {
                updateTimer.stop();
            }
            super.dispose();
        }
    }
    
    /**
     * Shows a progress dialog with timeout handling for a long-running task.
     */
    public static boolean showProgressWithTimeout(Component parent, Future<?> task, String operationName) {
        TimeoutProgressDialog dialog = new TimeoutProgressDialog(parent, task, operationName);
        
        // Show dialog in a separate thread to avoid blocking
        SwingUtilities.invokeLater(() -> dialog.setVisible(true));
        
        // Wait for task completion or cancellation
        try {
            while (!task.isDone() && !dialog.wasCancelled()) {
                Thread.sleep(100);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            dialog.cancel();
            return false;
        }
        
        dialog.dispose();
        return !dialog.wasCancelled();
    }
    
    /**
     * Creates a timeout monitor for a task without showing a dialog immediately.
     */
    public static TimeoutMonitor monitorTask(Future<?> task, Component parent, String operationName) {
        return new TimeoutMonitor(task, parent, operationName);
    }
}