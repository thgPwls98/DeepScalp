package com.example.Deepscalp;
import android.annotation.SuppressLint;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Objects;

public class Fragment1 extends Fragment {

    private WebView webView;

    private String keyword;

    public Fragment1() {
        // Required empty public constructor
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {




        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_1, container, false);

        // WebView 초기화
        webView = view.findViewById(R.id.webView);
        webView.getSettings().setJavaScriptEnabled(true); // JavaScript 허용
        webView.setWebViewClient(new WebViewClient()); // 내부 웹 페이지로 로드

        // 전달된 데이터 받기
        Bundle bundle = getArguments();
        if (bundle != null) {
            String stringData = bundle.getString("stringKey");
            if (Objects.equals(stringData, "탈모")) {
                keyword="32465";
            }
            else if (Objects.equals(stringData, "비듬")) {
                keyword="32480";
            }
            else if (Objects.equals(stringData, "미세각질")||Objects.equals(stringData, "피지과다")) {
                keyword="31535";
            }
            else if (Objects.equals(stringData, "모낭사이홍반")||Objects.equals(stringData, "모낭홍반농포")) {
                keyword="31544";
            }
        }
        // 필요한 데이터로 WebView 설정
        String url = "https://www.amc.seoul.kr/asan/healthinfo/disease/diseaseDetail.do?contentId=" + keyword;
        webView = view.findViewById(R.id.webView);
        webView.getSettings().setJavaScriptEnabled(true); // JavaScript 허용
        webView.setWebViewClient(new WebViewClient()); // 내부 WebView로 로드
        webView.loadUrl(url); // URL 로드

        return view;
    }
}
