package com.matburt.mobileorg.Synchronizers;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Pattern;

import android.content.Context;
import android.content.res.Resources.NotFoundException;
import android.preference.PreferenceManager;
import android.util.Log;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;
import com.matburt.mobileorg.MobileOrgDatabase;
import com.matburt.mobileorg.R;
import com.matburt.mobileorg.Error.ReportableError;

public class ScpSynchronizer extends Synchronizer {

    private class SyncUserInfo implements UserInfo {

        @Override
        public String getPassphrase() {
            return getPassword();
        }

        @Override
        public boolean promptPassphrase(String message) {
            return true;
        }

        @Override
        public boolean promptPassword(String message) {
            return true;
        }

        @Override
        public void showMessage(String arg0) {}

        @Override
        public String getPassword() {
            return (ScpSynchronizer.this.appSettings.getString("scpPass", ""));            
        }

        @Override
        public boolean promptYesNo(String arg0) {
            return true;
        }
    };
      
    public ScpSynchronizer(Context parentContext) {
        this.rootContext = parentContext;
        this.r = this.rootContext.getResources();
        this.appdb = new MobileOrgDatabase((Context)parentContext);
        this.appSettings = PreferenceManager.getDefaultSharedPreferences(
                                   parentContext.getApplicationContext());
    }

    public boolean checkReady() {
        if (this.appSettings.getString("scpUrl", "").equals(""))
            return false;
        return true;
    }

    public void push() throws NotFoundException, ReportableError {
    }

    public void pull() throws NotFoundException, ReportableError {
        Pattern pattern = Pattern.compile(".*:.*\\.(?:org|txt)$");
        String url = this.appSettings.getString("scpUrl", "");
        if (!pattern.matcher(url).find()) {
            throw new ReportableError(
                    r.getString(R.string.error_bad_url, url), null);
        }

        // Get the index file.
        String masterStr = this.fetchOrgFileString(url);
        if (masterStr.equals("")) {
            throw new ReportableError(
                    r.getString(R.string.error_file_not_found, url), null);
        }
        HashMap<String, String> masterList = this.getOrgFilesFromMaster(masterStr);
        ArrayList<HashMap<String, Boolean>> todoLists = this.getTodos(masterStr);
        ArrayList<ArrayList<String>> priorityLists = this.getPriorities(masterStr);
        this.appdb.setTodoList(todoLists);
        this.appdb.setPriorityList(priorityLists);
        String urlRoot = this.getRootUrl(url);

        // Get the checksums file.
        masterStr = this.fetchOrgFileString(urlRoot + "checksums.dat");
        HashMap<String, String> newChecksums = this.getChecksums(masterStr);
        HashMap<String, String> oldChecksums = this.appdb.getChecksums();

        // Get dependencies.
        for (String key : masterList.keySet()) {
            if (oldChecksums.containsKey(key) &&
                newChecksums.containsKey(key) &&
                oldChecksums.get(key).equals(newChecksums.get(key)))
                continue;
            Log.d(LT, "Fetching: " + key + ": " + urlRoot + masterList.get(key));
            this.fetchAndSaveOrgFile(
                    urlRoot + masterList.get(key), masterList.get(key));
            this.appdb.addOrUpdateFile(
                    masterList.get(key), key, newChecksums.get(key));
        }
        
        // TODO: for efficiency, consider pulling directories.
    }

    public BufferedReader fetchOrgFile(String orgPath) throws NotFoundException, ReportableError {
        String user = appSettings.getString("scpUser", "");            
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        try {
            String host = orgPath.substring(0, orgPath.indexOf(':'));
            String remoteFile = orgPath.substring(orgPath.indexOf(':') + 1);

            JSch jsch = new JSch();
            Session session = jsch.getSession(user, host, 22);

            // Password will be given via UserInfo interface.
            session.setUserInfo(new SyncUserInfo());
            session.connect();

            String command = "scp -f " + remoteFile;
            ChannelExec channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);

            OutputStream out = channel.getOutputStream();
            InputStream in = channel.getInputStream();

            channel.connect();
            Log.d(LT, "Connecting...");

            byte[] buf = new byte[1024];

            // send '\0'
            buf[0] = 0; out.write(buf, 0, 1); out.flush();

            while(true) {
                int c = checkAck(in);
                if (c!='C') {
                    break;
                }

                // read '0644 '
                in.read(buf, 0, 5);

                Log.d(LT, "Reading file size...");

                // Read file size.
                long filesize = 0L;
                while(true) {
                    if (in.read(buf, 0, 1) < 0) {
                        // error
                        Log.e(LT, "Error reading file size.");
                        break; 
                    }
                    if (buf[0] == ' ') break;
                    filesize = filesize * 10L + (long)(buf[0] - '0');
                }

                // Read (and ignore) filename.
                // TODO: to reduced to while not 0x0a.
                for (int i = 0; ; i++) {
                    in.read(buf, i, 1);
                    if (buf[i] == (byte)0x0a)
                        break;
                }

                // send '\0'
                buf[0] = 0; out.write(buf, 0, 1); out.flush();

                // Fetch the file content.
                int sizeRead;
                while(true) {
                    if (buf.length < filesize)
                        sizeRead = buf.length;
                    else
                        sizeRead = (int)filesize;
                    sizeRead = in.read(buf, 0, sizeRead);
                    if (sizeRead < 0) {
                        // error 
                        Log.e(LT, "Content fetching interrupted. Remaining " + filesize);
                        break;
                    }
                    buffer.write(buf, 0, sizeRead);
                    filesize -= sizeRead;
                    if (filesize == 0L) break;
                }

                if(checkAck(in)!=0) {
                    // Error ?
                    Log.e(LT, "Failed to conclude copy");
                    break;
                }

                // send '\0'
                buf[0] = 0; out.write(buf, 0, 1); out.flush();
            }

            Log.d(LT, "disconnecting...");
            session.disconnect();
        }
        catch(Exception e) {
            throw new ReportableError("Error occured in secured copy", e);
        }
        
        // This is very under efficient, but matches the Synchronizer API. 
        return new BufferedReader(new StringReader(buffer.toString()));
    }

    static int checkAck(InputStream in) throws IOException {
        int b = in.read();
        // b may be 0 for success,
        //          1 for error,
        //          2 for fatal error,
        //         -1
        if (b == 1 || b == 2) {
            StringBuffer sb = new StringBuffer();
            int c;
            do {
                c=in.read();
                sb.append((char)c);
            }
            while(c!='\n');
            if(b==1){ // error
                System.out.print(sb.toString());
            }
            if(b==2){ // fatal error
                System.out.print(sb.toString());
            }
        }
        return b;
    }
    
    private String getRootUrl(String url) throws NotFoundException, ReportableError {
        int i = url.lastIndexOf('/');
        return url.substring(0, i + 1);
    }
}
