# Railway Deployment Guide - Dev Environment

This guide will help you deploy the Spawn App Backend to Railway as a separate dev environment.

## Prerequisites

- Railway account (https://railway.app)
- GitHub repository with your backend code
- Railway CLI (optional, but recommended)

## Step 1: Prepare Your Repository

The following files have been created for Railway deployment:

1. **Procfile** - Tells Railway how to start your application
2. **Dockerfile** - Defines the build process for your application
3. **application-railway.properties** - Configuration for Railway dev environment (with Redis)
4. **application-railway-no-redis.properties** - Configuration for quick deployment without Redis

Commit these files to your repository:

```bash
git add Procfile Dockerfile src/main/resources/application-railway.properties src/main/resources/application-railway-no-redis.properties
git commit -m "Add Railway deployment configuration"
git push
```

## Step 2: Create a New Railway Project

1. Go to [railway.app](https://railway.app) and log in
2. Click **"New Project"** in the top right
3. Select **"Deploy from GitHub repo"**
4. Choose your `Spawn-App-Back-End` repository
5. Click **"Deploy Now"**

## Step 3: Add Required Services

Your application needs MySQL and Redis. Add them in Railway:

### Add MySQL Database

1. In your Railway project, click **"New Service"**
2. Select **"Database"** → **"MySQL"**
3. Railway will create a MySQL instance

### Add Redis (Optional - for Quick Deployment Skip This)

For a quick deployment without Redis, you can skip this step and use simple in-memory caching instead. This is suitable for testing but not recommended for production.

1. Click **"New Service"** again
2. Select **"Database"** → **"Redis"**
3. Railway will create a Redis instance

**Quick Deployment (No Redis):**
- Skip adding the Redis service
- Set environment variable `SPRING_PROFILES_ACTIVE=railway-no-redis` instead of `railway`
- The application will use simple in-memory caching instead of Redis

## Step 4: Configure Environment Variables

You need to set environment variables for your application. Go to your backend service in Railway and click **"Variables"** tab.

### Database Variables (MySQL)

Railway automatically provides these for your MySQL service. Click on your MySQL service, go to the **"Variables"** tab, and reference these in your backend service:

- `MYSQL_URL` - The JDBC URL (format: `jdbc:mysql://host:port/database`)
- `MYSQL_USER` - Database username
- `MYSQL_PASSWORD` - Database password

To reference Railway's MySQL service variables in your backend:
1. In your backend service Variables tab
2. Add: `MYSQL_URL` = `${{MYSQL_SERVICE.DATABASE_URL}}` (replace `MYSQL_SERVICE` with your actual MySQL service name)
3. Add: `MYSQL_USER` = `${{MYSQL_SERVICE.DATABASE_USER}}`
4. Add: `MYSQL_PASSWORD` = `${{MYSQL_SERVICE.DATABASE_PASSWORD}}`

### Redis Variables (Only if using Redis)

If you added the Redis service, reference these variables:

- `REDIS_HOST` = `${{REDIS_SERVICE.HOSTNAME}}`
- `REDIS_PORT` = `${{REDIS_SERVICE.PORT}}`
- `REDIS_PASSWORD` = `${{REDIS_SERVICE.PASSWORD}}`

**Skip this section if you're doing a quick deployment without Redis.**

### Other Required Variables

Add these manually to your backend service Variables:

- `EMAIL_PASS` - Your Gmail app password for email sending
- `APNS_CERTIFICATE` - Path to your APNS certificate (or base64 encoded)
- `CERTIFICATE_PASSWORD` - APNS certificate password
- `APNS_BUNDLE_ID` - Your iOS app bundle ID
- `GOOGLE_CLIENT_ID` - Your Google OAuth client ID
- `GOOGLE_ANDROID_CLIENT_ID` - Your Google Android OAuth client ID
- `APPLE_CLIENT_ID` - Your Apple OAuth client ID

## Step 5: Deploy

Once all variables are configured, Railway will automatically redeploy. You can:

1. Click the **"Deploy"** button in your backend service
2. Monitor the build logs to ensure everything builds successfully
3. Once deployed, Railway will provide a public URL for your API

## Step 6: Test Your Deployment

1. Copy the public URL from your Railway backend service
2. Test the health endpoint (if you have one) or any API endpoint
3. Verify database connectivity by checking logs

## Railway-Specific Configuration Notes

### application-railway.properties

The Railway profile includes:
- MySQL database connection (configured via environment variables)
- Redis caching (configured via environment variables)
- Optimized connection pooling for Railway's resource limits
- APNS set to `production=false` for dev environment
- All other production features enabled

### Dockerfile

Uses a multi-stage build for:
- Smaller final image size
- Faster builds (dependency caching)
- JRE-only runtime (no JDK needed in production)

## Updating Your Dev Environment

To update your dev environment:

1. Push changes to your GitHub repository
2. Railway will automatically detect the commit and redeploy
3. Monitor the deployment logs

## Managing Costs

Railway has a free tier with limits. For a dev environment:
- The free tier should be sufficient for testing
- You can pause services when not in use to save credits
- Monitor your usage in the Railway dashboard

## Troubleshooting

### Build Failures

- Check the build logs in Railway
- Ensure Java 17 is being used (configured in Dockerfile)
- Verify all dependencies are in pom.xml

### Database Connection Issues

- Verify environment variables are correctly set
- Check that MySQL service is running
- Review Railway service logs for connection errors

### Redis Connection Issues

- Verify Redis service is running
- Check Redis environment variables
- Ensure Redis is not disabled in configuration

## Switching Between Environments

Your application now has four profiles:

- **Default** (production) - Uses `application.properties`
- **Dev** (local) - Uses `application-dev.properties` with H2 database
- **Railway** (dev cloud with Redis) - Uses `application-railway.properties` with Railway services including Redis
- **Railway No Redis** (dev cloud quick) - Uses `application-railway-no-redis.properties` with Railway services but simple in-memory caching

To run locally with dev profile:
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

**Choosing a Railway profile:**
- Use `railway` (with Redis) for full-featured dev environment closer to production
- Use `railway-no-redis` for quick deployment/testing when you don't need distributed caching

## Next Steps

1. Set up a separate Railway project for production when ready
2. Consider using Railway's preview deployments for pull requests
3. Add health check endpoints for monitoring
4. Set up logging aggregation (Railway provides built-in logs)
