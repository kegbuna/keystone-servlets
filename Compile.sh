#!/bin/sh
javac -cp ../lib/*:../../../../lib/*:. KeystoneServletBase.java
javac -cp ../lib/*:../../../../lib/*:. RequestListServlet.java
javac -cp ../lib/*:../../../../lib/*:. PageDataServlet.java
javac -cp ../lib/*:../../../../lib/*:. RequestCreateServlet.java
javac -cp ../lib/*:../../../../lib/*:. MenuListServlet.java
javac -cp ../lib/*:../../../../lib/*:. RequestDataServlet.java
javac -cp ../lib/*:../../../../lib/*:. WorklogDataServlet.java
javac -cp ../lib/*:../../../../lib/*:. WorklogCreateServlet.java
javac -cp ../lib/*:../../../../lib/*:. RequestUpdateServlet.java
javac -cp ../lib/*:../../../../lib/*:. AttachmentServlet.java
javac -cp ../lib/*:../../../../lib/*:. AttachmentCreateServlet.java
javac -cp ../lib/*:../../../../lib/*:. FileDownloadServlet.java

touch ../web.xml
