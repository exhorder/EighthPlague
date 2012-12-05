// @author Tim O'Brien
// @name noisyGhost.sc
// @desc Swarm-based synthesis, controlled/influenced by a beagleboard and sensors in a jar.

// QUICK INSTRUCTIONS: Select all and execute

//------------------------------------------------------
// For debugging
//------------------------------------------------------
//s.meter;
//OSCFunc.trace(true); // Turn posting on
//OSCFunc.trace(false); // Turn posting off
//SwingOSC.quitAll

s.boot;
s.meter;

// Synths
// FM synth (4 channel)
SynthDef(\simpFMAZ,
{ | outbus=0, freq=200, modfreq=290, modamp=0.5, amp=0.3, dur=0.5, pos=0 |
  var sig, mod, env, atk, dec, sus, rel;
  atk = dur*0.01;
  dec = dur*0.5;
  sus = dur*0.02;
  rel = dur*0.47;
  mod = SinOsc.ar(modfreq, 0, 0.5);
  sig = SinOsc.ar(freq + (freq * mod), 0, 1.0);
  env = Env([0, 1, 0.6, 0.4, 0]*amp, [atk, dec, sus, rel], curve: \squared);
  Out.ar(outbus,PanAz.ar(4,sig * EnvGen.kr(env, doneAction: 2), pos));
}).add;

// Conga drum sample synth (4 channel)
~paths = "/home/tim/EighthPlague_250A/EighthPlague/server/1302__ramjac__natal-quinto/*.aif".pathMatch;
~drumbufs = Array.newClear(~paths.size);
for (0, ~paths.size-1, {arg i; ~drumbufs[i] = Buffer.read(s,PathName(~paths[i]).asAbsolutePath);} );
SynthDef(\natalQuintoAz, { arg out = 0, bufnum, sampdur, amp=1, pos=0;
    var env;
    env = EnvGen.kr(Env.linen(sampdur*0.001,sampdur*0.9,sampdur*0.0999,amp),doneAction: 2);
    Out.ar( out,
        PanAz.ar(4, env * PlayBuf.ar(1, bufnum, BufRateScale.kr(bufnum)), pos)
    );
}).add;

SynthDef(\bubbles, {
    | out=0, maxdelay=0.02, mindelay=0.02, decay=4, gate=1.0, freq=4, amp=1.0, pos=0.0, dur |
    var sig, env, atk, dec, sus, rel;
    atk = dur*0.01;
    dec = dur*0.5;
    sus = dur*0.02;
    rel = dur*0.47;
    sig = CombN.ar(
          SinOsc.ar(
            LFNoise1.kr(freq, 24, LFSaw.kr([8,7.23], 0, 3, 80) ).midicps,
            0,
            0.04),
          maxdelay, // max delay
          mindelay, // actual delay
          decay);
    env = Env([0, 1, 0.6, 0.4, 0]*amp, [atk, dec, sus, rel], curve: \squared);
    Out.ar(out,PanAz.ar(4,sig * EnvGen.kr(env, doneAction: 2), pos));
}).add;

SynthDef(\spaceDrone,{
  | out=0, amp=0.1, freq=200, pos=0.0, dur |
  var freqs, amps, phases;
  var env, atk, dec, sus, rel;
  atk = dur*0.01;
  dec = dur*0.5;
  sus = dur*0.02;
  rel = dur*0.47;
  freqs = freq * Array.rand(5, 0.25, 3.0);
  amps = Array.rand(5, 0.0, 1.0);
  phases = Array.rand(5, 0.0, 2*pi);
  env = Env([0, 1, 0.6, 0.4, 0]*amp, [atk, dec, sus, rel], curve: \squared);
  Out.ar(out, PanAz.ar(4, EnvGen.kr(env, doneAction: 2) * Klang.ar(`[ freqs, amps, phases ],1,0), pos));
}).add;

SynthDef(\ocean,{
  | out=0, amp=0.1, freq=200, pos=0.0, dur |
  var sig, env, atk, dec, sus, rel;
  atk = dur*0.4;
  dec = dur*0.1;
  sus = dur*0.02;
  rel = dur*0.48;
  sig = OnePole.ar(WhiteNoise.ar(0.1)+Dust.ar(freq, 0.5), 0.7);
  sig = sig + Splay.ar(FreqShift.ar(sig, 1/(4..7)));
  env = Env([0, 1, 0.6, 0.4, 0]*amp, [atk, dec, sus, rel], curve: \squared);
  Out.ar(out, PanAz.ar(4, EnvGen.kr(env, doneAction: 2) * sig, pos));
}).add;


//------------------------------------------------------
// Receive notes from python swarm and make sound

~drums=true; // whether to use drums
~fm=false;    // whether to use FM synth
~bubbles=false;
~spacedrone=false;
~ocean=true;

~scale = Scale.major;

~processing = NetAddr("127.0.0.1", 57121);

// musical parameter mapping
~minFreq = 10;  // in midi notes
~maxFreq = 80; // in midi notes
~minAmp = 0;
~maxAmp = 0.5;
~minDur = 0.1;
~maxDur = 3.0;
~minPan = 0.0;
~maxPan = 2.0;
~minSpeedLim = 300.0;
~maxSpeedLim = 3000.0;
~rangeFreq = ~maxFreq - ~minFreq;
~rangeAmp = ~maxAmp - ~minAmp;
~rangeDur = ~maxDur - ~minDur;
~rangePan = ~maxPan - ~minPan;
~rangeSpeedLim = ~maxSpeedLim - ~minSpeedLim;

n = NetAddr("127.0.0.1", 57120); // local machine
OSCdef.newMatching(\incoming, {|msg, time, addr, recvPort| \matching.postln}, '/swarmNote', n); // path matching
a = OSCdef(\incomingNotePrint,
      {|msg, time, addr, recvPort|
      time.postln;   // post time
      msg.do({arg i; i.postln}); // post each part of the msg
      ~freq = ((~rangeFreq * msg[1]) + ~minFreq).round;
      ~amp = ((~rangeAmp * msg[2]) + ~minAmp);
      ~dur = ((~rangeDur * msg[3]) + ~minDur);
      ~pan = ((~rangePan * msg[4]) + ~minPan);
      //~pan2 = ((~rangePan * msg[4]) + ~minPan);
      ~degree = (~freq % 12) + (7 * ((~freq / 12).round - 6) );

      if (~fm,
          {Pbind(
            \instrument,  \simpFMAZ,
            \freq,        Pseq([~freq.midicps],1),
            \modfreq,     Pkey(\freq),
            \amp,         ~amp,
            \dur,         ~dur,
            \pos,         ~pan
          ).play;}
      );
      
      if (~bubbles,
          {Pbind(
            \instrument,  \bubbles,
            \freq,        Pseq([~freq.midicps/50],1),
            \amp,         ~amp,
            \dur,         ~dur,
            \pos,         (~pan + 0.5) % 2.0
          ).play;}
      );

      if (~spacedrone,
          {Pbind(
            \instrument,  \spaceDrone,
            \freq,        Pseq([~freq.midicps],1),
            \amp,         ~amp,
            \dur,         ~dur,
            \pos,         (~pan + 1.5) % 2.0
          ).play;}
      );

      if (~ocean,
          {Pbind(
            \instrument,  \ocean,
            \freq,        Pseq([~freq.midicps],1),
            \amp,         ~amp,
            \dur,         ~dur*[2,3].choose,
            \pos,         (~pan + 1.0) % 2.0
          ).play;}
      );

      if (~drums,
          {Pbind(
            \instrument,  \natalQuintoAz,
            //\freq,       Pseq([msg[1].midicps.round],1),
            #[\bufnum, \sampdur],
                          Prand(Array.fill(~paths.size,{arg i; [~drumbufs[i],~drumbufs[i].duration]}),6.rand),
            \amp,         ~amp*4,
            \dur,         Prand([0.1,0.1,0.2,0.5,1], inf),
            \pos,         (~pan + 1.0) % 2.0
          ).play;}
      );


      },
      '/swarmNote',nil);


//---------------------------------------------------------
// Receive messages from jar and send to swarm
//---------------------------------------------------------
// /piezo float
// /accel float float float
// /jerk 1

OSCdef(\newAccelMsg,
      {|msg, time, addr, recvPort|
//       \Accel_Msg.postln;
//       time.postln;
//       msg.postln;
      m = NetAddr("127.0.0.1", 9000); // python
      //set attractor position
      m.sendMsg("/attr_dim",
            0.asInt, //freq dimension
            (msg[1]).max(0).min(1)
      );
      m.sendMsg("/attr_dim",
            1.asInt, //amp dimension
            (msg[2]).max(0).min(1)
      );
      m.sendMsg("/attr_dim",
            2.asInt, //dur dimension
            (msg[3]).max(0).min(1)
      );
      m.sendMsg("/attr_dim",
            3.asInt, //pan dimension
            (msg[3]).max(0).min(1)
      );
      m.sendMsg("/attr_dim",
            4.asInt, //modfreq dimension
            (msg[2]).max(0).min(1)
      );
      m.sendMsg("/attr_dim",
            5.asInt, //ioi dimension
            (msg[3]*0.8).max(0).min(1)
      );
       
      },
      '/accel', nil);

OSCdef(\newJerkMsg,
      {|msg, time, addr, recvPort|
       \Jerk_Msg.postln;
       //time.postln;
       //addr.postln;
       //msg.postln;
       m = NetAddr("127.0.0.1", 9000); // python
       //set attractor position
       m.sendMsg("/resetboids");
       
       ~processing.sendMsg("/mode",[0,1].choose);
       ~processing.sendMsg("/radius",[10,20,27].choose;);
       ~processing.sendMsg("/rgb",78.rand+50,78.rand+50,78.rand+50);

       //play jerk tone
       {
        1.do({
          play({
            var sig, chain;
            sig = sum({ SinOsc.ar(rrand(50,6000),0,
                        2*Decay.ar(Dust2.ar(5),0.1)).tanh } ! 7);
            chain = sig;    // Start with the original signal
            8.do {|i|
                // A simple reverb
                chain = LeakDC.ar(
                  AllpassL.ar(LPF.ar(chain*0.9,3000),
                  0.2, {0.19.rand+0.01}!2, 3)
                  );
            };
            
            PanAz.ar(4,Limiter.ar(sig+chain)*EnvGen.kr(Env.sine(4,0.1.rand), doneAction: 2),2.0.rand);
          });
            2.wait;
        });
        }.fork;
        
        
        
       // choose synths to use        
      ~drums=[true,false].choose; // whether to use drums
      ~fm=[true,false].choose;    // whether to use FM synth
      ~bubbles=[true,false,false].choose;
      ~spacedrone=[true,false].choose;
      ~ocean=[true,false].choose;
      
      ~scale = Scale.major;

      ~processing = NetAddr("127.0.0.1", 57121);

      ~processing.sendMsg("/mode",[0,1].choose);
      ~processing.sendMsg("/jerk");


      // musical parameter mapping
      ~minFreq = 10;  // in midi notes
      ~maxFreq = 80; // in midi notes
      ~minAmp = 0;
      ~maxAmp = 0.5;
      ~minDur = 0.1;
      ~maxDur = 3.0;
      ~minPan = 0.0;
      ~maxPan = 2.0;
      ~minSpeedLim = 300.0;
      ~maxSpeedLim = 3000.0;
      ~rangeFreq = ~maxFreq - ~minFreq;
      ~rangeAmp = ~maxAmp - ~minAmp;
      ~rangeDur = ~maxDur - ~minDur;
      ~rangePan = ~maxPan - ~minPan;
      ~rangeSpeedLim = ~maxSpeedLim - ~minSpeedLim;

      n = NetAddr("127.0.0.1", 57120); // local machine
      OSCdef.newMatching(\incoming, {|msg, time, addr, recvPort| \matching.postln}, '/swarmNote', n); // path matching
      a = OSCdef(\incomingNotePrint,
            {|msg, time, addr, recvPort|
            time.postln;   // post time
            msg.do({arg i; i.postln}); // post each part of the msg
            ~freq = ((~rangeFreq * msg[1]) + ~minFreq).round;
            ~amp = ((~rangeAmp * msg[2]) + ~minAmp);
            ~dur = ((~rangeDur * msg[3]) + ~minDur);
            ~pan = ((~rangePan * msg[4]) + ~minPan);
            //~pan2 = ((~rangePan * msg[4]) + ~minPan);
            ~degree = (~freq % 12) + (7 * ((~freq / 12).round - 6) );

            if (~fm,
                {Pbind(
                  \instrument,  \simpFMAZ,
                  \freq,        Pseq([~freq.midicps],1),
                  \modfreq,     Pkey(\freq),
                  \amp,         ~amp,
                  \dur,         ~dur,
                  \pos,         ~pan
                ).play;}
            );
            
            if (~bubbles,
                {Pbind(
                  \instrument,  \bubbles,
                  \freq,        Pseq([~freq.midicps/50],1),
                  \amp,         ~amp,
                  \dur,         ~dur,
                  \pos,         ~pan
                ).play;}
            );

            if (~spacedrone,
                {Pbind(
                  \instrument,  \spaceDrone,
                  \freq,        Pseq([~freq.midicps],1),
                  \amp,         ~amp,
                  \dur,         ~dur,
                  \pos,         (~pan + 1.5) % 2.0
                ).play;}
            );
            
            if (~ocean,
                {Pbind(
                  \instrument,  \ocean,
                  \freq,        Pseq([~freq.midicps],1),
                  \amp,         ~amp,
                  \dur,         ~dur*[2,3].choose,
                  \pos,         (~pan + 1.0) % 2.0
                ).play;}
            );
            
            if (~drums,
                {Pbind(
                  \instrument,  \natalQuintoAz,
                  //\freq,       Pseq([msg[1].midicps.round],1),
                  #[\bufnum, \sampdur],
                                Prand(Array.fill(~paths.size,{arg i; [~drumbufs[i],~drumbufs[i].duration]}),4.rand),
                  \amp,         ~amp*4,
                  \dur,         Prand([0.1,0.2,0.5,1], inf),
                  \pos,         (~pan + 1.0) % 2.0
                ).play;}
            );


            },
            '/swarmNote',nil);


        
        
        
      },
      '/jerk', nil);

OSCdef(\newPiezoMsg,
      {|msg, time, addr, recvPort|
       //\Piezo_Msg.postln;
       //time.postln;
       msg.postln;
       m = NetAddr("127.0.0.1", 9000); // python
       //set attractor position
       m.sendMsg("/speed",
             (msg[1]).max(0.3).min(1)
       );
      },
      '/piezo', nil);
