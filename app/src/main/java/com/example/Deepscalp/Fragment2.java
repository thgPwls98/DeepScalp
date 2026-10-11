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

public class Fragment2 extends Fragment {

    private WebView webView;
    private String keyword;

    public Fragment2() {
        // Required empty public constructor
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_2, container, false);

        // WebView 초기화
        webView = view.findViewById(R.id.webView);
        webView.getSettings().setJavaScriptEnabled(true); // JavaScript 허용
        webView.setWebViewClient(new WebViewClient()); // 내부 웹 페이지로 로드

        Bundle bundle = getArguments();
        if (bundle != null) {
            String stringData = bundle.getString("stringKey");
            if (Objects.equals(stringData, "탈모")) {
                keyword=stringData;
            }
            else if (Objects.equals(stringData, "비듬")) {
                keyword=stringData+"관리";
            }
            else if (Objects.equals(stringData, "미세각질")||Objects.equals(stringData, "피지과다")) {
                keyword="지루성 두피염 관리";
            }
            else if (Objects.equals(stringData, "모낭사이홍반")||Objects.equals(stringData, "모낭홍반농포")) {
                keyword="두피 모낭염 관리";
            }
        }
        // 필요한 데이터로 WebView 설정
        String url = "https://www.youtube.com/results?search_query=" + keyword;
        webView = view.findViewById(R.id.webView);
        webView.getSettings().setJavaScriptEnabled(true); // JavaScript 허용
        webView.setWebViewClient(new WebViewClient()); // 내부 WebView로 로드
        webView.loadUrl(url); // URL 로드
        return view;
    }
}
