package org.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

public class HttpURLConnectionExample {

    private static final String GET_URL = "http://gw-6d26.sandbox.tek.sdu.dk/ssapi/zb/dev/1";// Here you need to format the string based on your gateway and endpoint of interest

    public static void main(String[] args) throws IOException {
        sendGET();
    }

    private static void sendGET() throws IOException {
        URL obj = new URL(GET_URL);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        con.setRequestMethod("GET");
        //con.setRequestProperty("User-Agent", USER_AGENT);
        int responseCode = con.getResponseCode();// Response code 200 or 201 is success, otherwise there is a problem.
        System.out.println("GET Response Code :: " + responseCode);



        if (responseCode == HttpURLConnection.HTTP_OK) { // success
            BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = reader.readLine()) != null) {
                response.append(inputLine);
            }
            reader.close();
            JSONObject json = new JSONObject(String.valueOf(response));

            // print result
            System.out.println(json.toString());
        }
        else {
            System.out.println("GET failed");
        }

    }

}