package com.example.rapidjava;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.example.rapidjava.adapter.CardAdapter;
import com.example.rapidjava.model.Card;
import com.example.rapidjava.model.Dutam;
import com.example.rapidjava.stripeConnect.StripeClient;
import com.example.rapidjava.stripeConnect.StripeServices;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

public class Mijn_kaarten extends AppCompatActivity {

    StripeServices stripeServices;

    //create composite for api call and thread

    CompositeDisposable compositeDisposable=new CompositeDisposable();

    RecyclerView recyclerView;
    LinearLayoutManager manager;

    List<Dutam> cardList;
    //create a variable here
    boolean Pay=false;
    int amount=0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mijn_kaarten);

        //call stripe client and create an instance to connect stripe
        stripeServices= StripeClient.getInstance().create(StripeServices.class);

        recyclerView=findViewById(R.id.cardViewrecycler);
        manager=new LinearLayoutManager(this);
        recyclerView.setLayoutManager(manager);

        //get Pay parameter and pass it to adapter
        Pay=getIntent().getBooleanExtra("Pay",false);
        if(Pay){
            amount=getIntent().getIntExtra("amount",0);
        }

        cardList=new ArrayList<>();

        //create a function loadCard
        loadCard();

    }

//    private void loadCard() {
//        //we need two params url and type
//        //first add customer id to get their card
//
//        String url="customers/cus_Mc7ZSR4MPhtAXi/payment_methods";
//        String type="card";
//        //using composite call api
//        compositeDisposable.add(stripeServices.getCard(url,type)
//                        .subscribeOn(Schedulers.io())
//                .observeOn(AndroidSchedulers.mainThread())
//                .subscribe(result-> {
//                 //if success
//                    //get data here 4digits given by stripe
//                    for (int i=0;i<result.getData().size();i++){
//                        cardList.add(result.getData().get(i));
//                    }
//
//                    CardAdapter cardAdapter= new CardAdapter(this,cardList,Pay,amount);
//                    recyclerView.setAdapter(cardAdapter);
//                },throwable -> {
//                    //if we get thread exception then add thread message
//                    System.out.println(throwable.getMessage());
//                })
//        );
//    }

    private void loadCard() {
        String url = "customers/cus_Mc7ZSR4MPhtAXi/payment_methods";
        String type = "card";

        compositeDisposable.add(stripeServices.getCard(url, type)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    // Check if the result contains cards
                    if (result.getData() == null || result.getData().isEmpty()) {
                        // No cards found
                        showNoCardsUI();
                    } else {
                        // Cards found, add to list and show RecyclerView
                        for (int i = 0; i < result.getData().size(); i++) {
                            cardList.add(result.getData().get(i));
                        }
                        CardAdapter cardAdapter = new CardAdapter(this, cardList, Pay, amount);
                        recyclerView.setAdapter(cardAdapter);
                        recyclerView.setVisibility(View.VISIBLE);
                    }
                }, throwable -> {
                    // Handle errors
                    System.out.println(throwable.getMessage());
                })
        );
    }

    private void showNoCardsUI() {
        // Show the "no cards" message and button
        TextView noCardsMessage = findViewById(R.id.noCardsMessage);
        Button addCardButton = findViewById(R.id.addCardButton);

        noCardsMessage.setVisibility(View.VISIBLE);
        addCardButton.setVisibility(View.VISIBLE);

        addCardButton.setOnClickListener(v -> {
            // Navigate to the "Add a Card" screen
            Intent intent = new Intent(Mijn_kaarten.this, AddCard.class);
            startActivity(intent);
        });
    }


}