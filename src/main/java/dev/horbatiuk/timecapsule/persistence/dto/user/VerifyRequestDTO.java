package dev.horbatiuk.timecapsule.persistence.dto.user;

public class VerifyRequestDTO {

    private String token;

    public VerifyRequestDTO(String token) {
        this.token = token;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
