package com.example.Deepscalp;
//로그인 할 때 객체를 어떤식으로 담을 것인지 //사용자 계정 정보 모델 클래스
public class UserAccount {

    private String idtoken;
    private String emailId;
    private String password;
    private String profileImageUrl;
    public UserAccount(){ }

    public String getIdtoken() {
        return idtoken;
    }

    public void setIdtoken(String idtoken) {
        this.idtoken = idtoken;
    }
    public String getEmailId() {
        return emailId;
    }

    public void setEmailId(String emailId) {
        this.emailId = emailId;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

}
