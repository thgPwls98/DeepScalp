package com.example.Deepscalp;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.tabs.TabLayout;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class Care extends AppCompatActivity {

    Toolbar toolbar;
    Fragment1 fragment1;
    Fragment2 fragment2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // EdgeToEdge 설정
        EdgeToEdge.enable(this);

        // Fragment 초기화
        fragment1 = new Fragment1();
        fragment2 = new Fragment2();


        /*Intent intent = getIntent();
        String value = intent.getStringExtra("key");*/

        // 다른 액티비티에서 데이터를 읽습니다.
        SharedPreferences sharedPref = getSharedPreferences("AppName", Context.MODE_PRIVATE);
        String value = sharedPref.getString("key", "default");

        Bundle bundle = new Bundle();
        bundle.putString("stringKey", value);
        fragment1.setArguments(bundle);
        fragment2.setArguments(bundle);


        // 레이아웃 설정
        setContentView(R.layout.activity_care);

        // Toolbar 설정
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);


        // 초기 Fragment 설정
        getSupportFragmentManager().beginTransaction().replace(R.id.container, fragment1).commit();

        // TabLayout 설정
        TabLayout tabs = findViewById(R.id.tabs);
        tabs.addTab(tabs.newTab().setText("질환백과"));
        tabs.addTab(tabs.newTab().setText("관리법"));

        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int position = tab.getPosition();
                Log.d("MainActivity", "선택된 탭: " + position);
                Fragment selected = null;
                if (position == 0) {
                    selected = fragment1;
                } else if (position == 1) {
                    selected = fragment2;
                }
                getSupportFragmentManager().beginTransaction().replace(R.id.container, selected).commit();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                // 아무 작업도 하지 않음
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                // 아무 작업도 하지 않음
            }
        });

        // WindowInsets 설정
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
}
