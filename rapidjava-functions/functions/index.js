const functions = require('firebase-functions');
const admin = require('firebase-admin');
admin.initializeApp();

const db = admin.firestore();
// const auth = admin.auth();

// Secure payment processing via QR code
exports.processQRPayment = functions.https.onCall(async (data, context) => {
  // Validate authentication
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'User not authenticated');
  }

  const {qrData, amount, recipientId, uuid, timestamp} = data;
  const payerId = context.auth.uid;

  try {
    // Validate input
    if (!qrData || !amount || !recipientId || !uuid) {
      throw new functions.https.HttpsError('invalid-argument', 'Missing required fields');
    }

    if (amount <= 0) {
      throw new functions.https.HttpsError('invalid-argument', 'Invalid amount');
    }

    if (payerId === recipientId) {
      throw new functions.https.HttpsError('invalid-argument', 'Cannot pay yourself');
    }

    if (Date.now() - timestamp > 5 * 60 * 1000) {
      throw new functions.https.HttpsError('deadline-exceeded', 'QR code expired');
    }

    // Check if token already used
    const usedTokenDoc = await db.collection('usedPaymentTokens').doc(uuid).get();
    if (usedTokenDoc.exists) {
      throw new functions.https.HttpsError('failed-precondition', 'QR code already used');
    }

    // Process payment transaction
    const result = await processPaymentTransaction(payerId, recipientId, amount, uuid, 'qr_code');

    // Log activity for both users
    await logPaymentActivity(payerId, recipientId, amount, 'payment_sent', uuid);
    await logPaymentActivity(recipientId, payerId, amount, 'payment_received', uuid);

    return {
      success: true,
      transactionId: uuid,
      newBalance: result.payerBalance
    };

  } catch (error) {
    console.error('Payment processing error:', error);
    throw new functions.https.HttpsError('internal', error.message);
  }
});

// Secure NFC payment processing
exports.processNFCPayment = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'User not authenticated');
  }

  const {merchantId, amount, timestamp, transactionId} = data;
  const payerId = context.auth.uid;

  try {
    // Validate NFC payment data
    if (!merchantId || !amount || !timestamp || !transactionId) {
      throw new functions.https.HttpsError('invalid-argument', 'Invalid NFC data');
    }

    // Check if payment is expired (5 minutes)
    if (Date.now() - timestamp > 5 * 60 * 1000) {
      throw new functions.https.HttpsError('deadline-exceeded', 'Payment request expired');
    }

    // Process payment
    const result = await processPaymentTransaction(payerId, merchantId, amount, transactionId, 'nfc');

    // Log activities
    await logPaymentActivity(payerId, merchantId, amount, 'payment_sent', transactionId);
    await logPaymentActivity(merchantId, payerId, amount, 'payment_received', transactionId);

    return {
      success: true,
      transactionId: transactionId,
      newBalance: result.payerBalance
    };

  } catch (error) {
    console.error('NFC payment error:', error);
    throw new functions.https.HttpsError('internal', error.message);
  }
});

// index.js
exports.topUpWallet = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
      throw new functions.https.HttpsError('unauthenticated', 'User not authenticated');
  }
  const { amount } = data;
  const userId = context.auth.uid;
  if (!amount || amount <= 0) {
      throw new functions.https.HttpsError('invalid-argument', 'Invalid amount');
  }
  try {
      const userRef = db.collection('users').doc(userId);
      const result = await db.runTransaction(async (transaction) => {
          const userDoc = await transaction.get(userRef);
          if (!userDoc.exists) {
              throw new Error('User account not found');
          }
          const currentBalance = userDoc.data().balance || 0;
          const newBalance = currentBalance + amount;
          transaction.update(userRef, {
              balance: newBalance,
              updatedAt: admin.firestore.FieldValue.serverTimestamp()
          });
          return { newBalance };
      });
      await logActivity(userId, 'top_up', `Wallet topped up with SRD ${amount}`, amount, true);
      return { success: true, newBalance: result.newBalance };
  } catch (error) {
      console.error('Top-up error:', error);
      throw new functions.https.HttpsError('internal', error.message);
  }
});

// Secure voucher validation and redemption
exports.redeemVoucher = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'User not authenticated');
  }

  const {voucherCode} = data;
  const userId = context.auth.uid;

  try {
    if (!voucherCode) {
      throw new functions.https.HttpsError('invalid-argument', 'Voucher code required');
    }

    const voucherRef = db.collection('vouchers').doc(voucherCode.toUpperCase());
    const userRef = db.collection('users').doc(userId);

    const result = await db.runTransaction(async (transaction) => {
      // Get voucher data
      const voucherDoc = await transaction.get(voucherRef);
      if (!voucherDoc.exists) {
        throw new Error('Voucher does not exist');
      }

      const voucherData = voucherDoc.data();

      // Check if voucher is used
      if (voucherData.used) {
        throw new Error('Voucher has already been used');
      }

      // Check if voucher is expired
      if (voucherData.expiresAt && Date.now() > voucherData.expiresAt.toMillis()) {
        throw new Error('Voucher has expired');
      }

      // Validate amount
      if (!voucherData.amount || voucherData.amount <= 0) {
        throw new Error('Invalid voucher amount');
      }

      // Get user data
      const userDoc = await transaction.get(userRef);
      if (!userDoc.exists) {
        throw new Error('User account not found');
      }

      const currentBalance = userDoc.data().balance || 0;
      const newBalance = currentBalance + voucherData.amount;

      // Update user balance
      transaction.update(userRef, {
        balance: newBalance,
        updatedAt: admin.firestore.FieldValue.serverTimestamp()
      });

      // Mark voucher as used
      transaction.update(voucherRef, {
        used: true,
        usedBy: userId,
        usedAt: admin.firestore.FieldValue.serverTimestamp()
      });

      return {
        amount: voucherData.amount,
        newBalance: newBalance
      };
    });

    // Log voucher redemption activity
    await logActivity(userId, 'voucher_redeemed',
      `Voucher of SRD ${result.amount} redeemed`,
      result.amount, true, null);

    return {
      success: true,
      amount: result.amount,
      newBalance: result.newBalance
    };

  } catch (error) {
    console.error('Voucher redemption error:', error);
    throw new functions.https.HttpsError('internal', error.message);
  }
});

// Helper function to process payment transactions
async function processPaymentTransaction(payerId, recipientId, amount, transactionId, paymentMethod) {
  return await db.runTransaction(async (transaction) => {
    const payerRef = db.collection('users').doc(payerId);
    const recipientRef = db.collection('users').doc(recipientId);

    // Get payer and recipient data
    const payerDoc = await transaction.get(payerRef);
    const recipientDoc = await transaction.get(recipientRef);

    if (!payerDoc.exists) {
      throw new Error('Payer account not found');
    }
    if (!recipientDoc.exists) {
      throw new Error('Recipient account not found');
    }

    const payerBalance = payerDoc.data().balance || 0;
    const recipientBalance = recipientDoc.data().balance || 0;

    // Check sufficient funds
    if (payerBalance < amount) {
      throw new Error('Insufficient funds');
    }

    // Update balances
    const newPayerBalance = payerBalance - amount;
    const newRecipientBalance = recipientBalance + amount;

    transaction.update(payerRef, {
      balance: newPayerBalance,
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    });

    transaction.update(recipientRef, {
      balance: newRecipientBalance,
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    });

    // Mark QR token as used if applicable
    if (paymentMethod === 'qr_code') {
      const tokenRef = db.collection('usedPaymentTokens').doc(transactionId);
      transaction.set(tokenRef, {
        usedBy: payerId,
        usedAt: admin.firestore.FieldValue.serverTimestamp(),
        amount: amount
      });
    }

    // Create transaction record
    const transactionData = {
      amount: amount,
      payerId: payerId,
      recipientId: recipientId,
      transactionId: transactionId,
      paymentMethod: paymentMethod,
      status: 'completed',
      timestamp: admin.firestore.FieldValue.serverTimestamp(),
      participants: [payerId, recipientId]
    };

    const transactionsRef = db.collection('transactions');
    transaction.set(transactionsRef.doc(), {
      ...transactionData,
      userId: payerId,
      isCredit: false,
      type: 'payment_sent'
    });

    transaction.set(transactionsRef.doc(), {
      ...transactionData,
      userId: recipientId,
      isCredit: true,
      type: 'payment_received'
    });

    return {
      payerBalance: newPayerBalance,
      recipientBalance: newRecipientBalance
    };
  });
}

// Helper function to log payment activities
async function logPaymentActivity(userId, counterpartyId, amount, activityType, transactionId) {
  let action, details;

  if (activityType === 'payment_sent') {
    action = 'Payment Sent';
    details = `Paid SRD ${amount} to user (TXN: ${transactionId})`;
  } else {
    action = 'Payment Received';
    details = `Received SRD ${amount} from user (TXN: ${transactionId})`;
  }

  return logActivity(userId, activityType, details, amount, activityType === 'payment_received', counterpartyId, transactionId);
}

// Main activity logging function with 30-day expiration
async function logActivity(userId, type, description, amount, isCredit, counterpartyId = null) {
  const activityData = {
    userId: userId,
    type: type,
    action: `${description.split(' ')[0]} ${description.split(' ')[1]}`, 
    details: description,
    amount: amount,
    isCredit: isCredit,
    counterpartyId: counterpartyId,
    timestamp: admin.firestore.FieldValue.serverTimestamp(),
    expiresAt: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000) // 30 days from now
  };

  // Add to user's activity log
  await db.collection('users').doc(userId).collection('activities').add(activityData);

  // Also add to global activities collection for backup
  await db.collection('activities').add(activityData);
}

// Scheduled function to clean up expired activities
exports.cleanupExpiredActivities = functions.pubsub.schedule('every 24 hours').onRun(async (context) => {
  console.log('Cleanup job started at:', context.timestamp);
  const expiryDate = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000);

  try {
    // Clean up user activities
    const usersSnapshot = await db.collectionGroup('activities')
      .where('expiresAt', '<=', expiryDate)
      .get();

    const batch = db.batch();
    usersSnapshot.docs.forEach(doc => {
      batch.delete(doc.ref);
    });

    // Clean up global activities
    const globalActivitiesSnapshot = await db.collection('activities')
      .where('expiresAt', '<=', expiryDate)
      .get();

    globalActivitiesSnapshot.docs.forEach(doc => {
      batch.delete(doc.ref);
    });

    await batch.commit();
    console.log(`Cleaned up ${usersSnapshot.size + globalActivitiesSnapshot.size} expired activities`);

  } catch (error) {
    console.error('Error cleaning up activities:', error);
  }
});

// Wallet balance check function
exports.getWalletBalance = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'User not authenticated');
  }

  const userId = context.auth.uid;

  try {
    const userDoc = await db.collection('users').doc(userId).get();

    if (!userDoc.exists) {
      // Create user document if it doesn't exist
      await db.collection('users').doc(userId).set({
        email: context.auth.token.email,
        balance: 0.0,
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
        updatedAt: admin.firestore.FieldValue.serverTimestamp()
      });
      return {balance: 0.0};
    }

    const userData = userDoc.data();
    return {balance: userData.balance || 0.0};

  } catch (error) {
    console.error('Balance check error:', error);
    throw new functions.https.HttpsError('internal', 'Failed to retrieve balance');
  }
});