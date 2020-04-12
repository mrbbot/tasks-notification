package com.mrbbot.taskification;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.tasks.TasksScopes;
import com.google.api.services.tasks.model.TaskLists;
import com.google.api.services.tasks.model.Tasks;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class TasksAPI {
    private static final String TAG = "TasksAPI";

    private static final DateTimeFormatter TASK_LOCAL_DATE = DateTimeFormatter.ofPattern("E dd MMM");

    public static class TaskList {
        String id;
        String title;

        private TaskList(String id, String title) {
            this.id = id;
            this.title = title;
        }

        @NonNull
        @Override
        public String toString() {
            return title;
        }
    }

    public static class Task {
        String id;
        String title;
        String notes;
        DateTime dueDateTime;
        List<Task> subTasks;
        SpannableStringBuilder spannable;

        private Task(String id, String title, String notes, DateTime dueDateTime, boolean isSubTask, LocalDateTime tomorrow, LocalDateTime afterTomorrow) {
            this.id = id;
            this.title = title;
            this.notes = notes;
            this.dueDateTime = dueDateTime;
            this.subTasks = new ArrayList<>();

            SpannableStringBuilder builder = new SpannableStringBuilder();
            if(isSubTask) {
                builder.append("   ");
            }
            builder.append("- ").append(title);
            boolean hasDue = dueDateTime != null;
            boolean hasNotes = notes != null;
            if(dueDateTime != null || notes != null) {
                builder.append(": ");
                int extraStart = builder.length();

                if(hasDue) {
                    // builder.append(dueDateTime.toString());
                    // builder.append(" ");

                    long value = dueDateTime.getValue();
                    LocalDateTime time = LocalDateTime.ofEpochSecond(value / 1000,0, ZoneOffset.UTC);

                    String dateString = time.format(TASK_LOCAL_DATE);
                    if(time.isBefore(tomorrow)) {
                        dateString = "Today";
                    } else if(time.isBefore(afterTomorrow)) {
                        dateString = "Tomorrow";
                    }
                    builder.append(dateString);

                    // times don't seem to be working :(
                    /*if(!dueDateTime.isDateOnly()) {
                        builder.append(" ").append(time.format(DateTimeFormatter.ISO_LOCAL_TIME));
                    }*/
                }
                if (hasDue && hasNotes) builder.append(", ");
                if(hasNotes) builder.append(notes);

                builder.setSpan(new ForegroundColorSpan(0xFF777777), extraStart, builder.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
            }
            this.spannable = builder;
        }

        private String toString(String prefix) {
            StringBuilder builder = new StringBuilder();
            builder.append(prefix).append("ID: ").append(id).append("\n");
            builder.append(prefix).append("  Title: ").append(title).append("\n");
            builder.append(prefix).append("  Notes: ").append(notes).append("\n");
            builder.append(prefix).append("  Due: ").append(dueDateTime).append("\n");
            builder.append(prefix).append("  Sub Tasks: ").append("\n");
            for (Task task : subTasks) {
                builder.append(task.toString(prefix + "    "));
            }
            return builder.toString();
        }

        @NonNull
        @Override
        public String toString() {
            return toString("");
        }
    }


    private static final Collection<String> TASK_SCOPES = Collections.singleton(TasksScopes.TASKS);
    private static HttpTransport HTTP_TRANSPORT = AndroidHttp.newCompatibleTransport();
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    @Nullable
    private static com.google.api.services.tasks.Tasks getService(Context context) {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(context);
        if (account == null) return null;

        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(context, TASK_SCOPES);
        credential.setSelectedAccount(account.getAccount());
        return new com.google.api.services.tasks.Tasks.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName("Tasks Notification")
                .build();
    }

    @Nullable
    static List<TaskList> getTaskLists(Context context) throws IOException {
        com.google.api.services.tasks.Tasks service = getService(context);
        if (service == null) return null;

        // get list of task lists
        TaskLists listsRes = service.tasklists().list().execute();
        List<TaskList> lists = new ArrayList<>();
        for (com.google.api.services.tasks.model.TaskList item : listsRes.getItems()) {
            lists.add(new TaskList(item.getId(), item.getTitle()));
        }
        return lists;
    }

    private static Comparator<com.google.api.services.tasks.model.Task> TASKS_COMPARATOR = new Comparator<com.google.api.services.tasks.model.Task>() {
        @Override
        public int compare(com.google.api.services.tasks.model.Task a, com.google.api.services.tasks.model.Task b) {
            if (a.getParent() != null && b.getParent() == null) {
                return 1;
            } else if (a.getParent() == null && b.getParent() != null) {
                return -1;
            }
            return a.getPosition().compareTo(b.getPosition());
        }
    };

    static List<Task> getTasks(Context context, String listId) throws IOException {
        LocalDateTime today = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS);
        LocalDateTime tomorrow = today.plusDays(1);
        LocalDateTime afterTomorrow = today.plusDays(2);

        List<Task> tasks = new ArrayList<>();

        com.google.api.services.tasks.Tasks service = getService(context);
        if (service == null) return tasks;

        // get list of tasks
        Tasks tasksRes = service
                .tasks()
                .list(listId)
                .setShowCompleted(false)
                .setShowDeleted(false)
                .setShowHidden(false)
                .execute();
        List<com.google.api.services.tasks.model.Task> items = tasksRes.getItems();
        if (items == null) return tasks;

        // sort tasks with sub tasks at the bottom then by position
        Collections.sort(items, TASKS_COMPARATOR);

        // map mapping task ID to task
        Map<String, Task> taskMap = new HashMap<>();

        // because of the sort, sub tasks will be visited last, by which point their parent
        // will be in taskMap
        for (com.google.api.services.tasks.model.Task item : items) {
            Task task = new Task(
                    item.getId(),
                    item.getTitle(),
                    item.getNotes(),
                    item.getDue(),
                    item.getParent() != null,
                    tomorrow,
                    afterTomorrow
            );
            taskMap.put(task.id, task);
            if (item.getParent() == null) {
                tasks.add(task);
            } else {
                Task parentTask = taskMap.get(item.getParent());
                if (parentTask != null) {
                    parentTask.subTasks.add(task);
                } else {
                    Log.w(TAG, task.id + "'s parent not found in task map!");
                }
            }
        }
        return tasks;
    }
}
