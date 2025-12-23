<a><img width="1660" height="412" alt="timecapsule-up" src="https://github.com/user-attachments/assets/433a0b7b-9788-479c-90e6-5b48ec000e70" /></a>

# TimeCapsule Backend<br> [![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://github.com/DanHorbatiuk/timecapsule/blob/main/LICENSE) [![Coverage](https://sonarcloud.io/api/project_badges/measure?project=timecapsule&metric=coverage)](https://sonarcloud.io/dashboard?id=timecapsule-backend) [![GitHub release](https://img.shields.io/static/v1?label=Pre-release&message=v.1.0.0&color=yellowgreen)](https://github.com/DanHorbatiuk/timecapsule/releases)

**Copyright 2025 Danylo Horbatiuk**

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the “Software”), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.


## 1. About the project

The primary objective of the “TimeCapsule” project is to save important data for transfer at the planned time in the future. After registration and verification, the user can create their time capsule with attachments and schedule the opening time. When the time to open the capsule comes up, the user will receive the title, description, and links to download attachments inside. <b> Easy, fast, and safe – TimeCapsule. </b>

## 2. Start the project locally

### 2.1. Required to install

* Java 21
* PostgreSQL 9.5+

### 2.2. How to run project

1. You should open in IntelliJ IDEA File -> `New Project` -> `Project From Version Control` -> `Repository URL` -> `URL` (https://github.com/DanHorbatiuk/timecapsule.git) -> `Clone`.
   
2. Open `Terminal` write `git checkout -b dev` (create new local branch "dev").
   
3. After this `git pull origin dev` (update last version from branch "dev").
   
4. You should create database `timecapsule`.
```shell
docker run -d --name timecapsule -p 5432:5432 -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB="timecapsule" -e POSTGRES_USER="timecapsule_admin" docker.io/postgres
```

5. Create `.env` file, path:`*path_to_your_project*/timecapsule/.env`.

Nessesary variables:

<table>
  <thead>
    <tr>
      <th>Variable Name</th>
      <th>Description</th>
      <th>Example</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><code>AWS_SERVICES_REGION</code></td>
      <td>AWS region</td>
      <td><code>us-east-1</code></td>
    </tr>
    <tr>
      <td><code>DB_PASSWORD</code></td>
      <td>Password for PostgreSQL</td>
      <td><code>your-db-password</code></td>
    </tr>
    <tr>
      <td><code>DB_URL</code></td>
      <td>JDBC URL for DB connection</td>
      <td><code>jdbc:postgresql://localhost:5432/timecapsule</code></td>
    </tr>
    <tr>
      <td><code>DB_USER</code></td>
      <td>Database username</td>
      <td><code>your-db-username</code></td>
    </tr>
    <tr>
      <td><code>JWT_SECRET</code></td>
      <td>Secret key for JWT tokens</td>
      <td><code>your-jwt-secret</code></td>
    </tr>
    <tr>
      <td><code>LAMBDA_ARN</code></td>
      <td>ARN of your AWS Lambda function</td>
      <td><code>arn:aws:lambda:...:function:sendCapsuleOpen</code></td>
    </tr>
    <tr>
      <td><code>MAIL_ADDRESS</code></td>
      <td>Email address used for sending mail</td>
      <td><code>noreply@yourdomain.com</code></td>
    </tr>
    <tr>
      <td><code>MAIL_APP_PASSWORD</code></td>
      <td>Application password for mail</td>
      <td><code>your-app-password</code></td>
    </tr>
    <tr>
      <td><code>MAIL_HOST</code></td>
      <td>SMTP server hostname</td>
      <td><code>smtp.gmail.com</code></td>
    </tr>
    <tr>
      <td><code>MAIL_PORT</code></td>
      <td>SMTP server port</td>
      <td><code>587</code></td>
    </tr>
    <tr>
      <td><code>S3_ACCESS_KEY</code></td>
      <td>AWS S3 access key</td>
      <td><code>your-s3-access-key</code></td>
    </tr>
    <tr>
      <td><code>S3_BUCKET</code></td>
      <td>Bucket name in S3</td>
      <td><code>your-s3-bucket</code></td>
    </tr>
    <tr>
      <td><code>S3_SECRET_KEY</code></td>
      <td>AWS S3 secret key</td>
      <td><code>your-s3-secret-key</code></td>
    </tr>
    <tr>
      <td><code>DATA_FOLDER_NAME</code></td>
      <td>Folder name for email payloads</td>
      <td><code>email</code></td>
    </tr>
    <tr>
      <td><code>FILE_FOLDER_NAME</code></td>
      <td>Folder name for uploaded files</td>
      <td><code>uploads</code></td>
    </tr>
    <tr>
      <td><code>SCHEDULER_ROLE_ARN</code></td>
      <td>IAM Role ARN for AWS Scheduler</td>
      <td><code>arn:aws:iam::...:role/SchedulerRole</code></td>
    </tr>
  </tbody>
</table>

6. Go to `Edit Configurations...` -> `Add New Configuration` -> `Spring Boot Application`:
* `Name` : `TimeCapsule App`
* `Run on`:`Local machine`
* `JDK` : `java 21`
* `Active profiles` : `prod`
* `Enviroment veriables` : `*path_to_your_project*/timecapsule/.env`

> [!IMPORTANT]
>   <b> After configuring AWS S3, EventBridge you should add Lambda function </b> `sendCapsuleOpen`:
> 
>   * `New Project` -> `Project From Version Control` -> `Repository URL` -> `URL` (https://github.com/DanHorbatiuk/timecapsule-lambda.git) -> `Clone`.
>   * Open `Terminal` write `mvn clean package`
>   * Get `/timecapsule/target/timecapsule-1.0.0-shaded.jar` and upload this jar in S3 storage
>   * In Lambda `sendCapsuleOpen` set function -> `File from S3 storage`

## 3. How to work with Swagger UI

1. Do full list of instructions in second article.

2. Open in your browser `http://localhost:8080/swagger-ui/index.html`

## 4. API Reference

<h3 align="center">API Endpoints</h3>
<table align="center">
  <thead>
    <tr>
      <th>Method</th>
      <th>Endpoint</th>
      <th>Description</th>
    </tr>
  </thead>
  <tbody>
    <tr><td colspan="3" align="center"><strong>User Profile</strong></td></tr>
    <tr><td>GET</td><td>/api/v1/me</td><td>Get current user profile</td></tr>
    <tr><td>PUT</td><td>/api/v1/me</td><td>Update current user profile</td></tr>
    <tr><td colspan="3" align="center"><strong>User Capsule Management</strong></td></tr>
    <tr><td>PUT</td><td>/api/v1/user/capsules/{capsuleId}</td><td>Edit capsule</td></tr>
    <tr><td>PATCH</td><td>/api/v1/user/capsules/{capsuleId}</td><td>Activate capsule</td></tr>
    <tr><td>POST</td><td>/api/v1/user/capsules/add</td><td>Add new capsule</td></tr>
    <tr><td>GET</td><td>/api/v1/user/capsules</td><td>Get user's capsules</td></tr>
    <tr><td colspan="3" align="center"><strong>Authentication</strong></td></tr>
    <tr><td>POST</td><td>/api/v1/auth/register</td><td>Register new user</td></tr>
    <tr><td>POST</td><td>/api/v1/auth/refresh-token</td><td>Refresh JWT token</td></tr>
    <tr><td>POST</td><td>/api/v1/auth/authenticate</td><td>Authenticate user</td></tr>
    <tr><td colspan="3" align="center"><strong>User Attachments</strong></td></tr>
    <tr><td>GET</td><td>/api/v1/user/{capsuleId}/attachments</td><td>Get attachments by capsule ID</td></tr>
    <tr><td>POST</td><td>/api/v1/user/{capsuleId}/attachments</td><td>Add attachment to capsule</td></tr>
    <tr><td>DELETE</td><td>/api/v1/user/{capsuleId}/attachments/{attachmentId}</td><td>Delete attachment</td></tr>
    <tr><td colspan="3" align="center"><strong>Account Verification</strong></td></tr>
    <tr><td>GET</td><td>/api/v1/verify</td><td>Verify user account</td></tr>
    <tr><td>GET</td><td>/api/v1/verify/send</td><td>Send verification email</td></tr>
    <tr><td colspan="3" align="center"><strong>Admin Capsule Management</strong></td></tr>
    <tr><td>DELETE</td><td>/api/v1/admin/capsules/{capsuleId}</td><td>Delete capsule by ID</td></tr>
    <tr><td>PATCH</td><td>/api/v1/admin/capsules/{capsuleId}</td><td>Update capsule status</td></tr>
    <tr><td>GET</td><td>/api/v1/admin/capsules/{userId}</td><td>Get all capsules by user ID</td></tr>
  </tbody>

</table>

## 5. AWS architecture design

<div align="center">
  <img width="771" height="381" alt="timecapsule_arch-Create capsule drawio" src="https://github.com/user-attachments/assets/e3641c2a-7dc4-4cd4-b6ab-420fd6224d87" />
  <br><br>
  <img width="982" height="362" alt="timecapsule_arch-Open capsule drawio" src="https://github.com/user-attachments/assets/85653970-3c6f-4fc5-88cb-a6ca19b5eada" />
</div>

<br>

<div>
  
> The application follows a resilient architecture designed for asynchronous capsule delivery. To create a capsule, the backend server must be running to process the request and store the data in the database and cloud storage. However, for opening and sending scheduled capsules (via email), the application does **not require** the backend to be running at the moment of delivery. Instead, AWS services (such as EventBridge, Lambda, and S3) handle the delivery independently, ensuring high reliability even in case of backend downtime.  
> This design prioritizes **reliability**, **scalability**, and **low operational coupling**.

</div>

<br>

## Contact

For questions, feedback, or support, feel free to contact the author:  
📧 horbatiukdan@gmail.com

<i>If you find this project useful, consider giving it a ⭐ on GitHub!</i>
