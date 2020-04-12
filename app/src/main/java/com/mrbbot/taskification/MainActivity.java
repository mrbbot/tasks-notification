package com.mrbbot.taskification;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.api.services.tasks.TasksScopes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.mrbbot.taskification.ForegroundService.SP_LIST_ID_KEY;
import static com.mrbbot.taskification.ForegroundService.SP_LIST_TITLE_KEY;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "TaskificationActivity";
    private static final int RC_SIGN_IN = 1;

    private GoogleSignInClient googleSignInClient;
    private SignInButton signInButton;
    private ImageView iconView;
    private TextView infoTextView;
    private Spinner taskListSpinner;
    private Button signOutButton;

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new Scope(TasksScopes.TASKS))
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        signInButton = findViewById(R.id.sign_in_button);
        iconView = findViewById(R.id.icon_view);
        infoTextView = findViewById(R.id.info_text_view);
        taskListSpinner = findViewById(R.id.task_list_spinner);
        signOutButton = findViewById(R.id.sign_out_button);

        signInButton.setSize(SignInButton.SIZE_WIDE);
        signInButton.setOnClickListener(this);
        signOutButton.setOnClickListener(this);

        prefs = getSharedPreferences(getString(R.string.preference_file_key), MODE_PRIVATE);

        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if(account != null && prefs.getString(SP_LIST_ID_KEY, null) != null) {
            startService(Actions.START);
        }
        updateUI(account);
    }

    @SuppressLint("ApplySharedPref")
    private void selectTaskList(@NonNull TasksAPI.TaskList taskList) {
        Log.d(TAG, "Setting list to " + taskList.id + "... (" + taskList.title + ")");
        prefs.edit().putString(SP_LIST_ID_KEY, taskList.id).putString(SP_LIST_TITLE_KEY, taskList.title).commit();
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if(account != null) {
            startService(Actions.START);
        }
    }

    private void setTaskLists(final List<TasksAPI.TaskList> lists) {
        taskListSpinner.setOnItemSelectedListener(null);
        ArrayAdapter<TasksAPI.TaskList> adapter = new ArrayAdapter<>(MainActivity.this, R.layout.spinner_item, lists);
        taskListSpinner.setAdapter(adapter);
        String selectedList = prefs.getString(SP_LIST_ID_KEY, null);
        if(selectedList == null && lists.size() > 0) {
            Log.d(TAG,"Setting default list...");
            selectTaskList(lists.get(0));
        } else if (selectedList != null) {
            Log.d(TAG,"Setting saved list...");
            for (int i = 0; i < lists.size(); i++) {
                if(lists.get(i).id.equals(selectedList)) {
                    taskListSpinner.setSelection(i);
                    break;
                }
            }
        }
        taskListSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectTaskList(lists.get(position));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    private void updateUI(@Nullable GoogleSignInAccount account) {
        signOutButton.setEnabled(true);
        if(account == null) {
            signInButton.setVisibility(View.VISIBLE);
            iconView.setVisibility(View.INVISIBLE);
            infoTextView.setVisibility(View.INVISIBLE);
            taskListSpinner.setVisibility(View.INVISIBLE);
            signOutButton.setVisibility(View.INVISIBLE);

        } else {
            signInButton.setVisibility(View.INVISIBLE);
            iconView.setVisibility(View.VISIBLE);
            infoTextView.setText(getString(R.string.app_greeting, account.getGivenName()));
            infoTextView.setVisibility(View.VISIBLE);
            taskListSpinner.setVisibility(View.VISIBLE);
            signOutButton.setVisibility(View.VISIBLE);

            new GetTaskListsTask(new OnTaskCompletedListener<List<TasksAPI.TaskList>>() {
                @Override
                public void onTaskCompleted(List<TasksAPI.TaskList> result) {
                    setTaskLists(result);
                }
            }).execute(this);
        }
    }

    private interface OnTaskCompletedListener<T> {
        void onTaskCompleted(T result);
    }

    private static class GetTaskListsTask extends AsyncTask<Context, Void, List<TasksAPI.TaskList>> {
        private OnTaskCompletedListener<List<TasksAPI.TaskList>> listener;

        GetTaskListsTask(OnTaskCompletedListener<List<TasksAPI.TaskList>> listener) {
            this.listener = listener;
        }

        @Override
        protected List<TasksAPI.TaskList> doInBackground(Context... params) {
            try {
                return TasksAPI.getTaskLists(params[0]);
            } catch (IOException e) {
                Log.e(TAG, "Error getting task lists: " + e.getMessage());
                return new ArrayList<>();
            }
        }

        @Override
        protected void onPostExecute(List<TasksAPI.TaskList> taskLists) {
            listener.onTaskCompleted(taskLists);
        }
    }

    @Override
    public void onClick(View v) {
        if(v == signInButton) {
            Intent signInIntent = googleSignInClient.getSignInIntent();
            startActivityForResult(signInIntent, RC_SIGN_IN);
        } else if(v == signOutButton) {
            prefs.edit().putString(SP_LIST_ID_KEY, null).apply();
            prefs.edit().putString(SP_LIST_TITLE_KEY, null).apply();
            signOutButton.setEnabled(false);
            googleSignInClient.signOut().addOnCompleteListener(new OnCompleteListener<Void>() {
                @Override
                public void onComplete(@NonNull Task<Void> task) {
                    updateUI(null);
                    startService(Actions.STOP);
                }
            });
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                updateUI(account);
                startService(Actions.START);
            } catch (ApiException e) {
                Log.w(TAG, "Sign in failed! " + e.getMessage());
                updateUI(null);
            }
        }
    }

    private void startService(Actions action) {
        Intent serviceIntent = new Intent(this, ForegroundService.class);
        serviceIntent.setAction(action.name());
        ContextCompat.startForegroundService(this, serviceIntent);
    }
}
