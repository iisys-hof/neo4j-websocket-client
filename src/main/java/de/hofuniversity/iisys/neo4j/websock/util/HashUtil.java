/*
 *  Copyright 2015 Institute of Information Systems, Hof University
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  under the License.
 */
package de.hofuniversity.iisys.neo4j.websock.util;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class creating a hash for a user-password combination to enable
 * more secure authentication.
 */
public class HashUtil
{
    private static final String ENCODING = "UTF-8";
    private static final String DEFAULT_METHOD = "SHA-512";
    private final String fMethod;

    /**
     * Creates a hash utility using the default SHA-512 hashing method.
     */
    public HashUtil()
    {
        this(DEFAULT_METHOD);
    }

    /**
     * Creates a hash utility using the given hashing method.
     * The given method must not be null and must be known to
     * MessageDigest.getInstance().
     *
     * @param method hashing method to use
     */
    public HashUtil(String method)
    {
        if(method == null || method.isEmpty())
        {
            throw new NullPointerException("no hashing method given");
        }

        fMethod = method;
    }

    /**
     * Creates a hash from the given user name and password as expected by the
     * server's default authentication;
     * The parameters must not be null.
     *
     * @param user name of the user
     * @param password the user's clear text password
     * @return hashed version for the server
     */
    public String hash(String user, char[] password)
    {
        String text = user + new String(password);
        byte[] hash = null;

        try
        {
            MessageDigest md = MessageDigest.getInstance(fMethod);
            hash = md.digest(text.getBytes(ENCODING));
        }
        catch (NoSuchAlgorithmException e)
        {
            e.printStackTrace();
        }
        catch (UnsupportedEncodingException e)
        {
            e.printStackTrace();
        }

        //convert to hex string
        StringBuilder hexString = new StringBuilder();
        for(byte b : hash)
        {
            hexString.append(Integer.toString(
                (b & 0xff) + 0x100, 16).substring(1));
        }

        return hexString.toString();
    }

    /**
     * Hashes the given combination of user name and password, optionally with
     * a specific hash function.
     * Parameters:
     *      -u user name
     *      -p password
     *      -h hash algorithm (optional)
     *
     * @param args argument vector
     */
    public static void main(String[] args) throws Exception
    {
        final String userParam = "-u";
        final String passParam = "-p";
        final String hashParam = "-h";

        String username = null;
        char[] password = null;
        String hashFunction = DEFAULT_METHOD;

        //read command line parameters
        String value = null;
        for(int i = 0; i < args.length; ++i)
        {
            value = args[i];

            if(userParam.equals(value)
                && args.length > i + 1)
            {
                username = args[++i];
            }
            else if(passParam.equals(value)
                && args.length > i + 1)
            {
                password = args[++i].toCharArray();
            }
            else if(hashParam.equals(value)
                && args.length > i + 1)
            {
                hashFunction = args[++i];
            }
        }

        //check for user name
        if(username == null)
        {
            System.out.println("incomplete parameters, usage:");
            System.out.println("$COMMAND -u username [-p password] "
                + "[-h hash_function]");
            System.exit(1);
        }

        //read password from command line if needed
        if(password == null)
        {
            System.out.print("please enter a password: ");

            password = System.console().readPassword();
        }

        //create hash if password was entered
        if(password != null && password.length > 0)
        {
            HashUtil util = new HashUtil(hashFunction);
            System.out.println(util.hash(username, password));
        }
        else
        {
            System.out.println("no password given");
            System.exit(1);
        }
    }
}
